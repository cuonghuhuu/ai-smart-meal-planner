package com.smartmealplanner.food;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact reviewed category mapping for the SMILING Vietnam workbook vocabulary. */
final class SmilingVietnamCategoryMapper {
    private static final Map<SourceCategory, String> CATEGORIES = Map.ofEntries(
            entry("Added fats", "Other added fats", "FATS_OILS"),
            entry("Added fats", "Vegetable oil (unfortified)", "FATS_OILS"),
            entry("Added sugars", "Sugar (non-fortified)", "SEASONINGS"),
            entry("Dairy products", "Cheese", "DAIRY_CHEESE"),
            entry("Dairy products", "Fluid or powdered milk (non-fortified)", "DAIRY_MILK"),
            entry(
                    "Dairy products",
                    "Sweetened dairy products/desserts "
                            + "(flan,custard,sweetened yoghurt,ice cream)",
                    "DAIRY"),
            entry("Dairy products", "Yoghurt, solid and drinkable", "DAIRY"),
            entry("Fruits", "Other fruit", "FRUITS"),
            entry("Fruits", "Vitamin C-rich fruit", "FRUITS"),
            entry(
                    "Grains & grain products",
                    "Refined grains and products, unenriched/unfortified",
                    "GRAINS"),
            entry(
                    "Grains & grain products",
                    "Whole grains and products, unenriched/unfortified",
                    "GRAINS"),
            entry(
                    "Legumes,nuts & seeds",
                    "Nuts,seeds,and unsweetened products",
                    "PROTEIN"),
            entry("Meat,fish & eggs", "Eggs", "PROTEIN_EGG"),
            entry("Meat,fish & eggs", "Fish without bones", "PROTEIN_SEAFOOD"),
            entry("Meat,fish & eggs", "MyFoods_Special Meats", "PROTEIN"),
            entry("Meat,fish & eggs", "Organ meat", "PROTEIN_MEAT"),
            entry("Meat,fish & eggs", "Other animal parts", "PROTEIN"),
            entry("Meat,fish & eggs", "Pork", "PROTEIN_MEAT"),
            entry("Meat,fish & eggs", "Poultry, rabbit", "PROTEIN"),
            entry("Meat,fish & eggs", "Red meat", "PROTEIN_MEAT"),
            entry("Meat,fish & eggs", "Seafood", "PROTEIN_SEAFOOD"),
            entry(
                    "Savory snacks",
                    "Savory snacks, salted,spiced,fried",
                    "PREPARED"),
            entry(
                    "Starchy roots & other starchy plant foods",
                    "Other starchy plant foods",
                    "VEG_ROOT"),
            entry(
                    "Starchy roots & other starchy plant foods",
                    "Vitamin C-rich starchy plant foods",
                    "VEG_ROOT"),
            entry(
                    "Sweetened snacks & desserts",
                    "Sweet snack foods (candy and chocolate)",
                    "PREPARED"),
            entry("Vegetables", "Other vegetables", "VEGETABLES"),
            entry(
                    "Vegetables",
                    "Vitamin A source other vegetables",
                    "VEGETABLES"),
            entry("Vegetables", "Vitamin C-rich vegetables", "VEGETABLES"));

    private static final Set<String> GROUPS = CATEGORIES.keySet().stream()
            .map(SourceCategory::foodGroup)
            .collect(Collectors.toUnmodifiableSet());

    private SmilingVietnamCategoryMapper() {
    }

    static Resolution resolve(String foodGroup, String foodSubgroup) {
        String normalizedGroup = trimToNull(foodGroup);
        String normalizedSubgroup = trimToNull(foodSubgroup);
        String categoryCode = normalizedGroup == null || normalizedSubgroup == null
                ? null
                : CATEGORIES.get(new SourceCategory(normalizedGroup, normalizedSubgroup));
        return new Resolution(
                normalizedGroup != null && GROUPS.contains(normalizedGroup),
                categoryCode != null,
                categoryCode);
    }

    private static Map.Entry<SourceCategory, String> entry(
            String foodGroup,
            String foodSubgroup,
            String categoryCode) {
        return Map.entry(new SourceCategory(foodGroup, foodSubgroup), categoryCode);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SourceCategory(String foodGroup, String foodSubgroup) {
    }

    record Resolution(boolean groupKnown, boolean subgroupKnown, String categoryCode) {
        boolean subgroupCompatible() {
            return subgroupKnown;
        }
    }
}
