package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.recipe.RecipeRecommendationCandidate;

/** In-memory pantry balance used only during one plan generation. */
final class VirtualPantry {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final List<Balance> balances;

    private VirtualPantry(List<Balance> balances) {
        this.balances = balances;
    }

    static VirtualPantry from(
            List<PantryAvailabilitySnapshot> snapshots,
            Map<String, MeasurementUnitReferenceSnapshot> units) {
        List<Balance> balances = new ArrayList<>();
        for (PantryAvailabilitySnapshot snapshot : snapshots) {
            MeasurementUnitReferenceSnapshot unit = units.get(snapshot.unitCode());
            if (unit == null || snapshot.quantityRemaining() == null
                    || snapshot.quantityRemaining().signum() < 0) {
                continue;
            }
            balances.add(new Balance(
                    snapshot.ingredientPublicId(),
                    snapshot.quantityRemaining(),
                    unit,
                    snapshot.expiryDate()));
        }
        return new VirtualPantry(balances);
    }

    BigDecimal coverage(RecipeRecommendationCandidate candidate,
            int generatedServings, LocalDate targetDate,
            Map<String, MeasurementUnitReferenceSnapshot> units) {
        int applicableLines = 0;
        BigDecimal total = ZERO;
        for (RecipeRecommendationCandidate.Ingredient line : candidate.ingredients()) {
            LineCoverage lineCoverage = lineCoverage(candidate, line, generatedServings,
                    targetDate, units);
            if (line.optional() && !lineCoverage.present()) {
                continue;
            }
            applicableLines++;
            total = total.add(lineCoverage.coverage());
        }
        return applicableLines == 0
                ? ONE
                : total.divide(BigDecimal.valueOf(applicableLines), 12, RoundingMode.HALF_UP)
                        .max(ZERO).min(ONE);
    }

    BigDecimal expiryUrgency(RecipeRecommendationCandidate candidate,
            int generatedServings, LocalDate targetDate,
            Map<String, MeasurementUnitReferenceSnapshot> units) {
        BigDecimal highest = ZERO;
        for (RecipeRecommendationCandidate.Ingredient line : candidate.ingredients()) {
            if (line.quantity() == null || line.unitCode() == null) {
                continue;
            }
            MeasurementUnitReferenceSnapshot unit = units.get(line.unitCode());
            if (unit == null) {
                continue;
            }
            BigDecimal required = scaledQuantity(line.quantity(), generatedServings,
                    candidate.servings());
            MeasurementUnitReferenceSnapshot targetUnit = unit;
            for (Balance balance : balances) {
                if (balance.ingredientPublicId().equals(line.publicId())
                        && compatible(balance.unit(), targetUnit)
                        && usable(balance, targetDate)
                        && balance.quantityBase().signum() > 0
                        && balance.quantityBase().compareTo(toBase(required, targetUnit)) >= 0) {
                    highest = highest.max(urgency(balance.expiryDate(), targetDate));
                }
            }
        }
        return highest;
    }

    void consume(RecipeRecommendationCandidate candidate, int generatedServings,
            LocalDate targetDate, Map<String, MeasurementUnitReferenceSnapshot> units) {
        for (RecipeRecommendationCandidate.Ingredient line : candidate.ingredients()) {
            if (line.quantity() == null || line.unitCode() == null) {
                continue;
            }
            MeasurementUnitReferenceSnapshot targetUnit = units.get(line.unitCode());
            if (targetUnit == null) {
                continue;
            }
            BigDecimal remaining = toBase(
                    scaledQuantity(line.quantity(), generatedServings, candidate.servings()),
                    targetUnit);
            List<Balance> usable = balances.stream()
                    .filter(balance -> balance.ingredientPublicId().equals(line.publicId()))
                    .filter(balance -> compatible(balance.unit(), targetUnit))
                    .filter(balance -> usable(balance, targetDate))
                    .filter(balance -> balance.quantityBase().signum() > 0)
                    .sorted(Comparator.comparing(Balance::expiryDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (Balance balance : usable) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal consumed = balance.quantityBase().min(remaining);
                balance.subtract(consumed);
                remaining = remaining.subtract(consumed);
            }
        }
    }

    private LineCoverage lineCoverage(
            RecipeRecommendationCandidate candidate,
            RecipeRecommendationCandidate.Ingredient line,
            int generatedServings,
            LocalDate targetDate,
            Map<String, MeasurementUnitReferenceSnapshot> units) {
        if (line.quantity() == null || line.unitCode() == null) {
            return new LineCoverage(hasUsableIngredient(line.publicId(), targetDate),
                    hasUsableIngredient(line.publicId(), targetDate) ? ONE : ZERO);
        }
        MeasurementUnitReferenceSnapshot unit = units.get(line.unitCode());
        if (unit == null) {
            return new LineCoverage(false, ZERO);
        }
        BigDecimal requiredBase = toBase(
                scaledQuantity(line.quantity(), generatedServings, candidate.servings()), unit);
        BigDecimal available = balances.stream()
                .filter(balance -> balance.ingredientPublicId().equals(line.publicId()))
                .filter(balance -> compatible(balance.unit(), unit))
                .filter(balance -> usable(balance, targetDate))
                .map(Balance::quantityBase)
                .reduce(ZERO, BigDecimal::add);
        if (requiredBase.signum() <= 0) {
            return new LineCoverage(false, ZERO);
        }
        return new LineCoverage(available.signum() > 0,
                available.divide(requiredBase, 12, RoundingMode.HALF_UP)
                        .min(ONE).max(ZERO));
    }

    private boolean hasUsableIngredient(UUID ingredientPublicId, LocalDate targetDate) {
        return balances.stream()
                .anyMatch(balance -> balance.ingredientPublicId().equals(ingredientPublicId)
                        && usable(balance, targetDate)
                        && balance.quantityBase().signum() > 0);
    }

    private static BigDecimal scaledQuantity(BigDecimal quantity, int servings,
            Short recipeServings) {
        return quantity.multiply(BigDecimal.valueOf(servings))
                .divide(BigDecimal.valueOf(recipeServings), 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBase(BigDecimal quantity,
            MeasurementUnitReferenceSnapshot unit) {
        return unit.factorToBaseUnit() == null
                ? quantity
                : quantity.multiply(unit.factorToBaseUnit());
    }

    private static boolean compatible(MeasurementUnitReferenceSnapshot left,
            MeasurementUnitReferenceSnapshot right) {
        if (left.unitType() != right.unitType()) {
            return false;
        }
        if (left.unitType() == MeasurementUnitType.COUNT) {
            return left.code().equals(right.code());
        }
        String leftBase = left.baseUnitCode() == null ? left.code() : left.baseUnitCode();
        String rightBase = right.baseUnitCode() == null ? right.code() : right.baseUnitCode();
        return leftBase.equals(rightBase);
    }

    private static boolean usable(Balance balance, LocalDate targetDate) {
        return balance.expiryDate() == null || !balance.expiryDate().isBefore(targetDate);
    }

    private static BigDecimal urgency(LocalDate expiry, LocalDate targetDate) {
        if (expiry == null) {
            return ZERO;
        }
        long days = ChronoUnit.DAYS.between(targetDate, expiry);
        if (days < 0) return ZERO;
        if (days <= 1) return ONE;
        if (days <= 3) return new BigDecimal("0.75");
        if (days <= 7) return new BigDecimal("0.50");
        return new BigDecimal("0.25");
    }

    private record LineCoverage(boolean present, BigDecimal coverage) {
    }

    private static final class Balance {
        private final UUID ingredientPublicId;
        private BigDecimal quantityBase;
        private final MeasurementUnitReferenceSnapshot unit;
        private final LocalDate expiryDate;

        private Balance(UUID ingredientPublicId, BigDecimal quantity,
                MeasurementUnitReferenceSnapshot unit, LocalDate expiryDate) {
            this.ingredientPublicId = ingredientPublicId;
            this.quantityBase = unit.factorToBaseUnit() == null
                    ? quantity : quantity.multiply(unit.factorToBaseUnit());
            this.unit = unit;
            this.expiryDate = expiryDate;
        }

        UUID ingredientPublicId() { return ingredientPublicId; }
        BigDecimal quantityBase() { return quantityBase; }
        MeasurementUnitReferenceSnapshot unit() { return unit; }
        LocalDate expiryDate() { return expiryDate; }
        void subtract(BigDecimal value) { quantityBase = quantityBase.subtract(value); }
    }
}
