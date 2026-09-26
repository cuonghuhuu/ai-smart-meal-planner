package com.smartmealplanner.pantry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.FoodReferenceQueryService;
import com.smartmealplanner.food.FoodReferenceSnapshot;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.pantry.web.AdjustPantryItemRequest;
import com.smartmealplanner.pantry.web.ConsumePantryItemRequest;
import com.smartmealplanner.pantry.web.CreatePantryItemRequest;
import com.smartmealplanner.pantry.web.PantryResponse;
import com.smartmealplanner.pantry.web.UpdatePantryItemRequest;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticated application service for owner-scoped Pantry lots. */
@Service
public class PantryService {

    private static final Collection<PantryItemStatus> OPEN_STATUSES =
            List.of(PantryItemStatus.AVAILABLE, PantryItemStatus.RESERVED);

    private final PantryItemRepository items;
    private final PantryItemEventRepository events;
    private final CurrentUserService currentUserService;
    private final IngredientReferenceQueryService ingredientReferences;
    private final FoodReferenceQueryService foodReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;
    private final Clock clock;

    @Autowired
    public PantryService(
            PantryItemRepository items,
            PantryItemEventRepository events,
            CurrentUserService currentUserService,
            IngredientReferenceQueryService ingredientReferences,
            FoodReferenceQueryService foodReferences,
            MeasurementUnitReferenceQueryService unitReferences) {
        this(
                items,
                events,
                currentUserService,
                ingredientReferences,
                foodReferences,
                unitReferences,
                Clock.systemUTC());
    }

    PantryService(
            PantryItemRepository items,
            PantryItemEventRepository events,
            CurrentUserService currentUserService,
            IngredientReferenceQueryService ingredientReferences,
            FoodReferenceQueryService foodReferences,
            MeasurementUnitReferenceQueryService unitReferences,
            Clock clock) {
        this.items = items;
        this.events = events;
        this.currentUserService = currentUserService;
        this.ingredientReferences = ingredientReferences;
        this.foodReferences = foodReferences;
        this.unitReferences = unitReferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PantryResponse.Item> list(UUID authenticatedPublicId,
            boolean includeClosed) {

        CurrentUserIdentity identity = identity(authenticatedPublicId);
        List<PantryItem> values = includeClosed
                ? items.findByUserId(identity.internalId())
                : items.findByUserIdAndStatuses(
                        identity.internalId(),
                        OPEN_STATUSES);
        return toResponses(values);
    }

    @Transactional(readOnly = true)
    public PantryResponse.Item get(
            UUID authenticatedPublicId,
            UUID pantryItemPublicId) {

        CurrentUserIdentity identity = identity(authenticatedPublicId);
        return toResponses(List.of(findOwned(identity, pantryItemPublicId)))
                .getFirst();
    }

    @Transactional
    public PantryResponse.Item create(
            UUID authenticatedPublicId,
            CreatePantryItemRequest request) {

        if (request == null) {
            throw invalid();
        }
        CurrentUserIdentity identity = identity(authenticatedPublicId);
        BigDecimal quantity = positiveQuantity(request.quantity(), "quantity");
        String unitCode = requiredUnitCode(request.unitCode());
        PantryStorageLocation storageLocation = requiredStorageLocation(
                request.storageLocation());
        MeasurementUnitReferenceSnapshot unit = resolveUnit(unitCode);
        IngredientReferenceSnapshot ingredient = ingredientReferences
                .resolveActiveByPublicId(request.ingredientPublicId())
                .orElseThrow(() -> new PantryException(
                        PantryFailure.INGREDIENT_NOT_FOUND));
        FoodReferenceSnapshot food = resolveOptionalFood(
                ingredient,
                request.foodPublicId());
        Expiry expiry = resolveExpiry(
                request.acquiredOn(),
                request.expiryDate(),
                request.expiryKind(),
                request.expiryConfidence());
        String note = normalizeNote(request.note());

        PantryItem item;
        try {
            item = new PantryItem(
                    UUID.randomUUID(),
                    identity.internalId(),
                    ingredient.internalId(),
                    food == null ? null : food.internalId(),
                    quantity,
                    unit.internalId(),
                    storageLocation,
                    request.acquiredOn(),
                    expiry.expiryDate(),
                    expiry.kind(),
                    expiry.confidence(),
                    note);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }

        PantryItem saved = items.saveAndFlush(item);
        events.saveAndFlush(new PantryItemEvent(
                saved.internalId(),
                PantryItemEventType.ADDED,
                quantity,
                quantity,
                note,
                now()));
        return getPersisted(identity, saved.publicId());
    }

    @Transactional
    public PantryResponse.Item updateMetadata(
            UUID authenticatedPublicId,
            UUID pantryItemPublicId,
            UpdatePantryItemRequest request) {

        if (request == null) {
            throw invalid();
        }
        if (request.hasQuantityProperty()) {
            throw invalid();
        }
        CurrentUserIdentity identity = identity(authenticatedPublicId);
        PantryItem item = findOwned(identity, pantryItemPublicId);
        PantryStorageLocation storageLocation = requiredStorageLocation(
                request.storageLocation());
        Expiry expiry = resolveExpiry(
                request.acquiredOn(),
                request.expiryDate(),
                request.expiryKind(),
                request.expiryConfidence());
        try {
            item.updateMetadata(
                    storageLocation,
                    request.acquiredOn(),
                    expiry.expiryDate(),
                    expiry.kind(),
                    expiry.confidence(),
                    normalizeNote(request.note()));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        items.saveAndFlush(item);
        return getPersisted(identity, item.publicId());
    }

    @Transactional
    public PantryResponse.Item adjust(
            UUID authenticatedPublicId,
            UUID pantryItemPublicId,
            AdjustPantryItemRequest request) {

        if (request == null) {
            throw invalid();
        }
        CurrentUserIdentity identity = identity(authenticatedPublicId);
        PantryItem item = findOwned(identity, pantryItemPublicId);
        BigDecimal delta = request.quantityDelta();
        BigDecimal remaining;
        try {
            remaining = item.adjust(delta);
        } catch (IllegalStateException exception) {
            throw new PantryException(PantryFailure.ITEM_NOT_OPEN);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        items.saveAndFlush(item);
        events.saveAndFlush(new PantryItemEvent(
                item.internalId(),
                PantryItemEventType.ADJUSTED,
                delta,
                remaining,
                normalizeNote(request.note()),
                now()));
        return getPersisted(identity, item.publicId());
    }

    @Transactional
    public PantryResponse.Item consume(
            UUID authenticatedPublicId,
            UUID pantryItemPublicId,
            ConsumePantryItemRequest request) {

        if (request == null) {
            throw invalid();
        }
        CurrentUserIdentity identity = identity(authenticatedPublicId);
        PantryItem item = findOwned(identity, pantryItemPublicId);
        LocalDateTime occurredAt = now();
        BigDecimal remaining;
        try {
            remaining = item.consume(request.quantity(), occurredAt);
        } catch (IllegalStateException exception) {
            throw new PantryException(PantryFailure.ITEM_NOT_OPEN);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        items.saveAndFlush(item);
        events.saveAndFlush(new PantryItemEvent(
                item.internalId(),
                PantryItemEventType.CONSUMED,
                request.quantity().negate(),
                remaining,
                normalizeNote(request.note()),
                occurredAt));
        return getPersisted(identity, item.publicId());
    }

    @Transactional
    public PantryResponse.Item discard(
            UUID authenticatedPublicId,
            UUID pantryItemPublicId,
            String note) {

        CurrentUserIdentity identity = identity(authenticatedPublicId);
        PantryItem item = findOwned(identity, pantryItemPublicId);
        LocalDateTime occurredAt = now();
        BigDecimal previousRemaining;
        try {
            previousRemaining = item.discard(occurredAt);
        } catch (IllegalStateException exception) {
            throw new PantryException(PantryFailure.ITEM_NOT_OPEN);
        }
        items.saveAndFlush(item);
        events.saveAndFlush(new PantryItemEvent(
                item.internalId(),
                PantryItemEventType.DISCARDED,
                previousRemaining.negate(),
                BigDecimal.ZERO,
                normalizeNote(note),
                occurredAt));
        return getPersisted(identity, item.publicId());
    }

    private CurrentUserIdentity identity(UUID authenticatedPublicId) {
        return currentUserService.getIdentity(authenticatedPublicId);
    }

    private PantryItem findOwned(
            CurrentUserIdentity identity,
            UUID pantryItemPublicId) {

        if (pantryItemPublicId == null) {
            throw new PantryException(PantryFailure.INVALID_REQUEST);
        }
        return items.findByPublicIdAndUserId(
                        PantryIds.uuidToBytes(pantryItemPublicId),
                        identity.internalId())
                .orElseThrow(() -> new PantryException(
                        PantryFailure.PANTRY_ITEM_NOT_FOUND));
    }

    private PantryResponse.Item getPersisted(
            CurrentUserIdentity identity,
            UUID publicId) {

        return toResponses(List.of(items.findByPublicIdAndUserId(
                        PantryIds.uuidToBytes(publicId),
                        identity.internalId())
                .orElseThrow(() -> new PantryException(
                        PantryFailure.CORRUPTED_PANTRY_DATA))))
                .getFirst();
    }

    private List<PantryResponse.Item> toResponses(List<PantryItem> values) {
        if (values.isEmpty()) {
            return List.of();
        }

        Set<Long> ingredientIds = new HashSet<>();
        Set<Long> foodIds = new HashSet<>();
        Set<Long> unitIds = new HashSet<>();
        for (PantryItem item : values) {
            ingredientIds.add(item.ingredientId());
            if (item.foodId() != null) {
                foodIds.add(item.foodId());
            }
            unitIds.add(item.unitId());
        }

        Map<Long, IngredientReferenceSnapshot> ingredientsById =
                ingredientReferences.resolveByInternalIds(ingredientIds);
        Map<Long, FoodReferenceSnapshot> foodsById =
                foodIds.isEmpty()
                        ? Map.of()
                        : foodReferences.resolveByInternalIds(foodIds);
        Map<Long, MeasurementUnitReferenceSnapshot> unitsById =
                unitReferences.resolveByInternalIds(unitIds);
        if (ingredientsById.size() != ingredientIds.size()
                || foodsById.size() != foodIds.size()
                || unitsById.size() != unitIds.size()) {
            throw inconsistentReferenceData();
        }

        return values.stream()
                .map(item -> response(
                        item,
                        ingredientsById.get(item.ingredientId()),
                        item.foodId() == null ? null : foodsById.get(item.foodId()),
                        unitsById.get(item.unitId())))
                .toList();
    }

    private static PantryResponse.Item response(
            PantryItem item,
            IngredientReferenceSnapshot ingredient,
            FoodReferenceSnapshot food,
            MeasurementUnitReferenceSnapshot unit) {

        if (ingredient == null || unit == null
                || (item.foodId() != null && food == null)) {
            throw inconsistentReferenceData();
        }
        return new PantryResponse.Item(
                item.publicId(),
                ingredient.publicId(),
                ingredient.code(),
                ingredient.displayName(),
                food == null ? null : food.publicId(),
                food == null ? null : food.code(),
                food == null ? null : food.displayName(),
                item.quantityInitial(),
                item.quantityRemaining(),
                unit.code(),
                unit.displayName(),
                item.storageLocation(),
                item.acquiredOn(),
                item.expiryDate(),
                item.expiryKind(),
                item.expiryConfidence(),
                item.status(),
                item.closedAt(),
                item.note(),
                item.version(),
                item.createdAt(),
                item.updatedAt());
    }

    private FoodReferenceSnapshot resolveOptionalFood(
            IngredientReferenceSnapshot ingredient,
            UUID publicId) {

        if (publicId == null) {
            return null;
        }
        FoodReferenceSnapshot food = foodReferences.resolveByPublicId(publicId)
                .orElseThrow(() -> new PantryException(
                        PantryFailure.FOOD_NOT_FOUND));
        if (!foodReferences.isMappedToIngredient(
                ingredient.internalId(),
                food.internalId())) {
            throw new PantryException(PantryFailure.FOOD_NOT_MAPPED);
        }
        return food;
    }

    private MeasurementUnitReferenceSnapshot resolveUnit(String code) {
        MeasurementUnitReferenceSnapshot unit = unitReferences
                .resolveByCodes(Set.of(code))
                .get(code);
        if (unit == null) {
            throw new PantryException(PantryFailure.UNIT_NOT_FOUND);
        }
        return unit;
    }

    private static String requiredUnitCode(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw invalid();
        }
        return normalized;
    }

    private static PantryStorageLocation requiredStorageLocation(
            PantryStorageLocation value) {

        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static BigDecimal positiveQuantity(BigDecimal value, String field) {
        try {
            return PantryItem.requirePositiveQuantity(value, field);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static Expiry resolveExpiry(
            LocalDate acquiredOn,
            LocalDate expiryDate,
            PantryExpiryKind requestedKind,
            PantryExpiryConfidence requestedConfidence) {

        PantryExpiryKind kind = requestedKind == null
                ? PantryExpiryKind.UNKNOWN
                : requestedKind;
        PantryExpiryConfidence confidence = requestedConfidence == null
                ? PantryExpiryConfidence.UNKNOWN
                : requestedConfidence;
        try {
            PantryItem.validateExpiry(
                    acquiredOn,
                    expiryDate,
                    kind,
                    confidence);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        return new Expiry(expiryDate, kind, confidence);
    }

    private static String normalizeNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw invalid();
        }
        return normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static PantryException invalid() {
        return new PantryException(PantryFailure.INVALID_REQUEST);
    }

    private static ReferenceDataIntegrityException inconsistentReferenceData() {
        return new ReferenceDataIntegrityException(
                "Pantry item references inconsistent catalog data");
    }

    private record Expiry(
            LocalDate expiryDate,
            PantryExpiryKind kind,
            PantryExpiryConfidence confidence) {
    }
}
