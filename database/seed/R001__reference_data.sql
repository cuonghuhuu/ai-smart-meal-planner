-- =============================================================================
-- AI Smart Meal Planner -- R001 reference data
-- Repeatable seed for the reference vocabularies declared in
-- database/schema/V001__initial_schema.sql. Target engine: MySQL 8.0.19+.
-- =============================================================================
-- Contains reference values only. No user accounts, no passwords, no hashes,
-- no tokens, no API keys, no connection strings, no personal data.
--
-- Idempotent by construction: every statement is INSERT ... ON DUPLICATE KEY
-- UPDATE keyed on the table's natural unique `code`. Re-running refreshes
-- display text without inserting duplicates and without changing surrogate ids
-- that live data already references. Nothing here deletes or truncates.
--
-- Two insert forms are used:
--   * `VALUES ... AS new` for tables with no foreign key to resolve.
--   * A derived `src` table joined to the referenced table, where a code has to
--     be translated into an id. This keeps the file readable and avoids the
--     deprecated VALUES() function.
--
-- Statement order matters: base measurement units precede the units that
-- convert to them, and nutrients follow the units they are expressed in.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Roles
-- -----------------------------------------------------------------------------
-- Two roles only. Catalog curation is the sole privileged action the product
-- needs (see DB-ADR-011); anything finer waits for a real requirement.
INSERT INTO roles (code, name, description) VALUES
    ('ROLE_USER',  'Application user',
     'Standard end user. Owns their own profile, pantry, and meal plans.'),
    ('ROLE_ADMIN', 'Catalog administrator',
     'May curate foods, ingredients, recipes, and reference vocabularies.')
AS new
ON DUPLICATE KEY UPDATE
    name        = new.name,
    description = new.description;

-- -----------------------------------------------------------------------------
-- Measurement units -- base units first
-- -----------------------------------------------------------------------------
-- Base units carry no conversion factor. Every other unit of the same type
-- converts to its base by an exact factor, so within a type conversion is pure
-- arithmetic. Cross-type conversion (volume to mass, count to mass) is NOT
-- expressible here and lives in ingredient_unit_conversions.
INSERT INTO measurement_units (code, display_name, unit_type, base_unit_id, factor_to_base_unit) VALUES
    ('g',    'gram',      'MASS',   NULL, NULL),
    ('ml',   'millilitre','VOLUME', NULL, NULL),
    ('piece','piece',     'COUNT',  NULL, NULL),
    ('kcal', 'kilocalorie','ENERGY',NULL, NULL)
AS new
ON DUPLICATE KEY UPDATE
    display_name = new.display_name,
    unit_type    = new.unit_type;

-- Derived units. `factor_to_base_unit` is the multiplier to the base unit, so
-- 1 kg = 1000 g and 1 tbsp = 15 ml exactly as defined by the metric spoon.
INSERT INTO measurement_units (code, display_name, unit_type, base_unit_id, factor_to_base_unit)
SELECT src.code, src.display_name, src.unit_type, base.id, src.factor
FROM (
    SELECT 'kg'    AS code, 'kilogram'         AS display_name, 'MASS'   AS unit_type, 'g'  AS base_code, 1000.000000000000 AS factor
    UNION ALL SELECT 'mg',   'milligram',        'MASS',   'g',  0.001000000000
    UNION ALL SELECT 'mcg',  'microgram',        'MASS',   'g',  0.000001000000
    UNION ALL SELECT 'oz',   'ounce',            'MASS',   'g',  28.349523125000
    UNION ALL SELECT 'lb',   'pound',            'MASS',   'g',  453.592370000000
    UNION ALL SELECT 'l',    'litre',            'VOLUME', 'ml', 1000.000000000000
    UNION ALL SELECT 'dl',   'decilitre',        'VOLUME', 'ml', 100.000000000000
    UNION ALL SELECT 'tsp',  'teaspoon',         'VOLUME', 'ml', 5.000000000000
    UNION ALL SELECT 'tbsp', 'tablespoon',       'VOLUME', 'ml', 15.000000000000
    UNION ALL SELECT 'cup',  'cup (metric)',     'VOLUME', 'ml', 250.000000000000
    UNION ALL SELECT 'floz', 'fluid ounce (US)', 'VOLUME', 'ml', 29.573529562500
    UNION ALL SELECT 'kj',   'kilojoule',        'ENERGY', 'kcal', 0.239005736138
) AS src
JOIN measurement_units AS base ON base.code = src.base_code
ON DUPLICATE KEY UPDATE
    display_name        = src.display_name,
    unit_type           = src.unit_type,
    base_unit_id        = base.id,
    factor_to_base_unit = src.factor;

-- -----------------------------------------------------------------------------
-- Nutrients
-- -----------------------------------------------------------------------------
-- Each nutrient names the unit its amounts are expressed in, so no stored
-- nutrition amount is ever unit-ambiguous. `is_core` marks the set the UI shows
-- by default and the planner scores against.
INSERT INTO nutrients (code, display_name, unit_id, nutrient_kind, is_core, display_order)
SELECT src.code, src.display_name, u.id, src.nutrient_kind, src.is_core, src.display_order
FROM (
    SELECT 'ENERGY'         AS code, 'Energy'             AS display_name, 'kcal' AS unit_code, 'ENERGY'        AS nutrient_kind, TRUE  AS is_core, 10 AS display_order
    UNION ALL SELECT 'PROTEIN',       'Protein',            'g',    'MACRONUTRIENT', TRUE,  20
    UNION ALL SELECT 'FAT_TOTAL',     'Total fat',          'g',    'MACRONUTRIENT', TRUE,  30
    UNION ALL SELECT 'FAT_SATURATED', 'Saturated fat',      'g',    'MACRONUTRIENT', FALSE, 40
    UNION ALL SELECT 'CARBOHYDRATE',  'Carbohydrate',       'g',    'MACRONUTRIENT', TRUE,  50
    UNION ALL SELECT 'SUGARS',        'Total sugars',       'g',    'MACRONUTRIENT', FALSE, 60
    UNION ALL SELECT 'FIBER',         'Dietary fibre',      'g',    'MACRONUTRIENT', TRUE,  70
    UNION ALL SELECT 'SODIUM',        'Sodium',             'mg',   'MINERAL',       TRUE,  80
    UNION ALL SELECT 'POTASSIUM',     'Potassium',          'mg',   'MINERAL',       FALSE, 90
    UNION ALL SELECT 'CALCIUM',       'Calcium',            'mg',   'MINERAL',       FALSE, 100
    UNION ALL SELECT 'IRON',          'Iron',               'mg',   'MINERAL',       FALSE, 110
    UNION ALL SELECT 'VITAMIN_A',     'Vitamin A',          'mcg',  'VITAMIN',       FALSE, 120
    UNION ALL SELECT 'VITAMIN_C',     'Vitamin C',          'mg',   'VITAMIN',       FALSE, 130
    UNION ALL SELECT 'VITAMIN_D',     'Vitamin D',          'mcg',  'VITAMIN',       FALSE, 140
    UNION ALL SELECT 'CHOLESTEROL',   'Cholesterol',        'mg',   'OTHER',         FALSE, 150
    UNION ALL SELECT 'WATER',         'Water',              'g',    'OTHER',         FALSE, 160
) AS src
JOIN measurement_units AS u ON u.code = src.unit_code
ON DUPLICATE KEY UPDATE
    display_name  = src.display_name,
    unit_id       = u.id,
    nutrient_kind = src.nutrient_kind,
    is_core       = src.is_core,
    display_order = src.display_order;

-- -----------------------------------------------------------------------------
-- Activity levels
-- -----------------------------------------------------------------------------
-- `energy_factor` multiplies basal metabolic rate. These are the conventional
-- physical-activity-level bands; Java applies them when it calculates a
-- suggested energy target, and the user can always override the result.
INSERT INTO activity_levels (code, display_name, description, energy_factor, display_order) VALUES
    ('SEDENTARY',    'Sedentary',
     'Little or no planned exercise; mostly sitting.',                1.200, 10),
    ('LIGHT',        'Lightly active',
     'Light exercise or sport one to three days a week.',             1.375, 20),
    ('MODERATE',     'Moderately active',
     'Moderate exercise or sport three to five days a week.',         1.550, 30),
    ('VERY_ACTIVE',  'Very active',
     'Hard exercise or sport six to seven days a week.',              1.725, 40),
    ('EXTRA_ACTIVE', 'Extra active',
     'Very hard daily exercise, or a physically demanding job.',      1.900, 50)
AS new
ON DUPLICATE KEY UPDATE
    display_name  = new.display_name,
    description   = new.description,
    energy_factor = new.energy_factor,
    display_order = new.display_order;

-- -----------------------------------------------------------------------------
-- Nutrition goals
-- -----------------------------------------------------------------------------
INSERT INTO nutrition_goals (code, display_name, description, display_order) VALUES
    ('LOSE_WEIGHT',    'Lose weight',
     'Aim for a moderate energy deficit.',                       10),
    ('MAINTAIN',       'Maintain weight',
     'Aim to match energy intake to expenditure.',               20),
    ('GAIN_WEIGHT',    'Gain weight',
     'Aim for a moderate energy surplus.',                       30),
    ('BUILD_MUSCLE',   'Build muscle',
     'Emphasise protein intake alongside a small surplus.',      40),
    ('EAT_HEALTHIER',  'Eat more healthily',
     'No weight target; emphasise variety and nutrient quality.', 50),
    ('REDUCE_WASTE',   'Reduce food waste',
     'Prioritise using what is already in the pantry.',          60)
AS new
ON DUPLICATE KEY UPDATE
    display_name  = new.display_name,
    description   = new.description,
    display_order = new.display_order;

-- -----------------------------------------------------------------------------
-- Dietary preferences
-- -----------------------------------------------------------------------------
-- `is_exclusionary` marks patterns that forbid foods outright, which the
-- recommender treats as a hard constraint. The rest are soft preferences.
INSERT INTO dietary_preferences (code, display_name, description, is_exclusionary, display_order) VALUES
    ('VEGETARIAN',    'Vegetarian',
     'Excludes meat, poultry, and fish.',                          TRUE,  10),
    ('VEGAN',         'Vegan',
     'Excludes all animal products.',                              TRUE,  20),
    ('PESCATARIAN',   'Pescatarian',
     'Excludes meat and poultry; includes fish and seafood.',      TRUE,  30),
    ('HALAL',         'Halal',
     'Follows halal dietary requirements.',                        TRUE,  40),
    ('KOSHER',        'Kosher',
     'Follows kosher dietary requirements.',                       TRUE,  50),
    ('GLUTEN_FREE',   'Gluten free',
     'Excludes gluten-containing grains.',                         TRUE,  60),
    ('DAIRY_FREE',    'Dairy free',
     'Excludes milk and milk-derived ingredients.',                TRUE,  70),
    ('LOW_CARB',      'Lower carbohydrate',
     'Prefers meals with a smaller share of carbohydrate.',        FALSE, 80),
    ('LOW_SODIUM',    'Lower sodium',
     'Prefers meals with less added salt.',                        FALSE, 90),
    ('HIGH_PROTEIN',  'Higher protein',
     'Prefers meals with a larger share of protein.',              FALSE, 100),
    ('MEDITERRANEAN', 'Mediterranean style',
     'Prefers vegetables, legumes, fish, and olive oil.',          FALSE, 110)
AS new
ON DUPLICATE KEY UPDATE
    display_name    = new.display_name,
    description     = new.description,
    is_exclusionary = new.is_exclusionary,
    display_order   = new.display_order;

-- -----------------------------------------------------------------------------
-- Allergens
-- -----------------------------------------------------------------------------
-- The commonly regulated major allergen groups. A curated vocabulary rather
-- than free text so that exclusion filtering can be exact.
INSERT INTO allergens (code, display_name, description, display_order) VALUES
    ('GLUTEN',      'Cereals containing gluten', 'Wheat, rye, barley, oats, spelt, and hybrids.', 10),
    ('CRUSTACEANS', 'Crustaceans',    'Prawns, crab, lobster, and similar.',            20),
    ('EGG',         'Eggs',           'Eggs and egg-derived ingredients.',              30),
    ('FISH',        'Fish',           'Fish and fish-derived ingredients.',             40),
    ('PEANUT',      'Peanuts',        'Peanuts and peanut-derived ingredients.',        50),
    ('SOY',         'Soybeans',       'Soybeans and soy-derived ingredients.',          60),
    ('MILK',        'Milk',           'Milk and milk-derived ingredients, incl. lactose.', 70),
    ('TREE_NUT',    'Tree nuts',      'Almonds, hazelnuts, walnuts, cashews, and similar.', 80),
    ('CELERY',      'Celery',         'Celery and celeriac.',                           90),
    ('MUSTARD',     'Mustard',        'Mustard seed and prepared mustard.',            100),
    ('SESAME',      'Sesame',         'Sesame seeds and sesame oil.',                  110),
    ('SULPHITES',   'Sulphur dioxide and sulphites', 'Above the commonly regulated threshold.', 120),
    ('LUPIN',       'Lupin',          'Lupin flour and lupin seeds.',                  130),
    ('MOLLUSCS',    'Molluscs',       'Mussels, squid, snails, and similar.',          140)
AS new
ON DUPLICATE KEY UPDATE
    display_name  = new.display_name,
    description   = new.description,
    display_order = new.display_order;

-- -----------------------------------------------------------------------------
-- Meal slot types
-- -----------------------------------------------------------------------------
-- Rows, not columns: the number of meals per day is a property of a plan, not of
-- the schema. `typical_time` is an advisory local wall-clock hint for reminders.
INSERT INTO meal_slot_types (code, display_name, display_order, typical_time, is_main_meal) VALUES
    ('BREAKFAST',       'Breakfast',        10, '07:30:00', TRUE),
    ('MORNING_SNACK',   'Morning snack',    20, '10:00:00', FALSE),
    ('LUNCH',           'Lunch',            30, '12:30:00', TRUE),
    ('AFTERNOON_SNACK', 'Afternoon snack',  40, '16:00:00', FALSE),
    ('DINNER',          'Dinner',           50, '19:00:00', TRUE),
    ('EVENING_SNACK',   'Evening snack',    60, '21:00:00', FALSE)
AS new
ON DUPLICATE KEY UPDATE
    display_name  = new.display_name,
    display_order = new.display_order,
    typical_time  = new.typical_time,
    is_main_meal  = new.is_main_meal;

-- -----------------------------------------------------------------------------
-- Food categories -- top level first, then children
-- -----------------------------------------------------------------------------
INSERT INTO food_categories (code, display_name, parent_category_id, description) VALUES
    ('GRAINS',      'Grains and cereals', NULL, 'Rice, wheat, oats, and products made from them.'),
    ('VEGETABLES',  'Vegetables',         NULL, 'Fresh, frozen, and preserved vegetables.'),
    ('FRUITS',      'Fruits',             NULL, 'Fresh, dried, and preserved fruits.'),
    ('PROTEIN',     'Protein foods',      NULL, 'Meat, poultry, fish, eggs, legumes, and nuts.'),
    ('DAIRY',       'Dairy',              NULL, 'Milk, cheese, yoghurt, and alternatives.'),
    ('FATS_OILS',   'Fats and oils',      NULL, 'Cooking oils, butter, and spreads.'),
    ('SEASONINGS',  'Seasonings',         NULL, 'Herbs, spices, salt, and condiments.'),
    ('BEVERAGES',   'Beverages',          NULL, 'Drinks other than plain water.'),
    ('PREPARED',    'Prepared foods',     NULL, 'Ready meals and composite products.'),
    ('OTHER',       'Other',              NULL, 'Anything not covered by another category.')
AS new
ON DUPLICATE KEY UPDATE
    display_name = new.display_name,
    description  = new.description;

-- Second level. The parent is resolved by code, so this statement is safe to
-- re-run and does not depend on generated id values.
INSERT INTO food_categories (code, display_name, parent_category_id, description)
SELECT src.code, src.display_name, parent.id, src.description
FROM (
    SELECT 'GRAINS_RICE'      AS code, 'Rice'              AS display_name, 'GRAINS'     AS parent_code, 'Rice varieties and rice products.'        AS description
    UNION ALL SELECT 'GRAINS_BREAD',    'Bread and bakery',  'GRAINS',     'Breads, rolls, and baked goods.'
    UNION ALL SELECT 'GRAINS_PASTA',    'Pasta and noodles', 'GRAINS',     'Wheat, rice, and egg noodles.'
    UNION ALL SELECT 'VEG_LEAFY',       'Leafy greens',      'VEGETABLES', 'Spinach, lettuce, cabbage, and similar.'
    UNION ALL SELECT 'VEG_ROOT',        'Root vegetables',   'VEGETABLES', 'Carrots, potatoes, beets, and similar.'
    UNION ALL SELECT 'VEG_ALLIUM',      'Onions and garlic', 'VEGETABLES', 'Onion, shallot, garlic, leek.'
    UNION ALL SELECT 'PROTEIN_MEAT',    'Meat',              'PROTEIN',    'Beef, pork, lamb, and game.'
    UNION ALL SELECT 'PROTEIN_POULTRY', 'Poultry',           'PROTEIN',    'Chicken, duck, turkey.'
    UNION ALL SELECT 'PROTEIN_SEAFOOD', 'Fish and seafood',  'PROTEIN',    'Fish, crustaceans, and molluscs.'
    UNION ALL SELECT 'PROTEIN_LEGUME',  'Legumes',           'PROTEIN',    'Beans, lentils, peas, and soy products.'
    UNION ALL SELECT 'PROTEIN_EGG',     'Eggs',              'PROTEIN',    'Eggs of any bird.'
    UNION ALL SELECT 'PROTEIN_NUT',     'Nuts and seeds',    'PROTEIN',    'Tree nuts, peanuts, and edible seeds.'
    UNION ALL SELECT 'DAIRY_MILK',      'Milk and cream',    'DAIRY',      'Fluid milk, cream, and milk alternatives.'
    UNION ALL SELECT 'DAIRY_CHEESE',    'Cheese',            'DAIRY',      'Fresh, soft, and hard cheeses.'
    UNION ALL SELECT 'SEASONING_HERB',  'Herbs and spices',  'SEASONINGS', 'Fresh and dried aromatics.'
    UNION ALL SELECT 'SEASONING_SAUCE', 'Sauces',            'SEASONINGS', 'Prepared sauces and condiments.'
) AS src
JOIN food_categories AS parent ON parent.code = src.parent_code
ON DUPLICATE KEY UPDATE
    display_name       = src.display_name,
    parent_category_id = parent.id,
    description        = src.description;

-- -----------------------------------------------------------------------------
-- Recipe tags
-- -----------------------------------------------------------------------------
INSERT INTO recipe_tags (code, display_name, tag_kind) VALUES
    ('VIETNAMESE',    'Vietnamese',       'CUISINE'),
    ('ASIAN',         'Asian',            'CUISINE'),
    ('MEDITERRANEAN', 'Mediterranean',    'CUISINE'),
    ('WESTERN',       'Western',          'CUISINE'),
    ('SOUP',          'Soup',             'MEAL_TYPE'),
    ('SALAD',         'Salad',            'MEAL_TYPE'),
    ('MAIN_DISH',     'Main dish',        'MEAL_TYPE'),
    ('SIDE_DISH',     'Side dish',        'MEAL_TYPE'),
    ('DESSERT',       'Dessert',          'MEAL_TYPE'),
    ('NO_COOK',       'No cooking needed','METHOD'),
    ('ONE_POT',       'One pot',          'METHOD'),
    ('BAKED',         'Baked',            'METHOD'),
    ('GRILLED',       'Grilled',          'METHOD'),
    ('STIR_FRIED',    'Stir fried',       'METHOD'),
    ('STEAMED',       'Steamed',          'METHOD'),
    ('VEGETARIAN',    'Vegetarian',       'DIET'),
    ('VEGAN',         'Vegan',            'DIET'),
    ('GLUTEN_FREE',   'Gluten free',      'DIET'),
    ('DAIRY_FREE',    'Dairy free',       'DIET'),
    ('HIGH_PROTEIN',  'High protein',     'DIET'),
    ('QUICK',         'Quick to make',    'OTHER'),
    ('BUDGET',        'Budget friendly',  'OTHER'),
    ('MEAL_PREP',     'Good for meal prep','OTHER')
AS new
ON DUPLICATE KEY UPDATE
    display_name = new.display_name,
    tag_kind     = new.tag_kind;

-- -----------------------------------------------------------------------------
-- AI score components
-- -----------------------------------------------------------------------------
-- Declaring the scale here is what makes a stored score interpretable later:
-- a value of 0.8 is meaningless without knowing the range and direction it was
-- reported on. All components below are normalised to [0, 1].
INSERT INTO ai_score_components (code, display_name, description, scale_min, scale_max, higher_is_better) VALUES
    ('NUTRITION_FIT',   'Nutrition fit',
     'How closely the item moves the day towards the user''s nutrition targets.',
     0, 1, TRUE),
    ('PANTRY_COVERAGE', 'Pantry coverage',
     'Share of the required ingredients already available in the pantry.',
     0, 1, TRUE),
    ('EXPIRY_URGENCY',  'Expiry urgency',
     'Priority gained by consuming pantry items that expire soonest.',
     0, 1, TRUE),
    ('PREFERENCE_MATCH','Preference match',
     'Agreement with declared dietary preferences and past acceptances.',
     0, 1, TRUE),
    ('VARIETY',         'Variety',
     'Dissimilarity from recently planned meals, to avoid repetition.',
     0, 1, TRUE),
    ('EFFORT_FIT',      'Effort fit',
     'Agreement between required cooking time and the time the user has.',
     0, 1, TRUE),
    ('DISLIKE_PENALTY', 'Dislike penalty',
     'Penalty applied for disliked ingredients. Lower is better.',
     0, 1, FALSE)
AS new
ON DUPLICATE KEY UPDATE
    display_name     = new.display_name,
    description      = new.description,
    scale_min        = new.scale_min,
    scale_max        = new.scale_max,
    higher_is_better = new.higher_is_better;

-- -----------------------------------------------------------------------------
-- Notification types
-- -----------------------------------------------------------------------------
INSERT INTO notification_types (code, display_name, description, default_enabled, supports_lead_time) VALUES
    ('PANTRY_EXPIRING',  'Pantry item expiring soon',
     'Sent when a pantry item with a known expiry date is approaching it.',
     TRUE,  TRUE),
    ('PANTRY_EXPIRED',   'Pantry item expired',
     'Sent when a pantry item has passed its use-by date.',
     TRUE,  FALSE),
    ('MEAL_PLAN_REMINDER', 'Meal reminder',
     'Reminds the user of the next planned meal.',
     FALSE, TRUE),
    ('PLAN_ENDING',      'Meal plan ending',
     'Sent when the active plan is close to its end date.',
     TRUE,  TRUE),
    ('WEEKLY_SUMMARY',   'Weekly summary',
     'Summarises adherence, nutrition, and pantry waste for the past week.',
     FALSE, FALSE),
    ('MEASUREMENT_REMINDER', 'Weight check-in reminder',
     'Reminds the user to record a weight measurement.',
     FALSE, FALSE)
AS new
ON DUPLICATE KEY UPDATE
    display_name       = new.display_name,
    description        = new.description,
    default_enabled    = new.default_enabled,
    supports_lead_time = new.supports_lead_time;

-- -----------------------------------------------------------------------------
-- Ingredient groups
-- -----------------------------------------------------------------------------
-- Culinary role groups used to widen substitution candidate search. Membership
-- alone never asserts that two members are interchangeable; only a typed edge in
-- ingredient_substitutions does, and Java still re-checks allergens.
INSERT INTO ingredient_groups (code, display_name, description, group_kind) VALUES
    ('LEAFY_GREENS',    'Leafy greens',
     'Interchangeable mainly in salads and quick sautes.',         'CULINARY_ROLE'),
    ('HARD_CHEESE',     'Hard cheeses',
     'Grating cheeses used for seasoning and gratins.',            'CULINARY_ROLE'),
    ('NEUTRAL_OIL',     'Neutral cooking oils',
     'High smoke point oils with little flavour of their own.',    'CULINARY_ROLE'),
    ('AROMATIC_ALLIUM', 'Aromatic alliums',
     'Onion family aromatics used as a flavour base.',             'CULINARY_ROLE'),
    ('STARCHY_BASE',    'Starchy staples',
     'Rice, pasta, potatoes, and similar meal bases.',             'CULINARY_ROLE'),
    ('LEAN_PROTEIN',    'Lean protein sources',
     'Similar protein density and cooking behaviour.',             'NUTRITION_ROLE'),
    ('PLANT_MILK',      'Plant milks',
     'Dairy-free milk alternatives.',                              'CULINARY_ROLE'),
    ('SWEETENER',       'Sweeteners',
     'Sugars and syrups; sweetness and moisture differ.',          'CULINARY_ROLE')
AS new
ON DUPLICATE KEY UPDATE
    display_name = new.display_name,
    description  = new.description,
    group_kind   = new.group_kind;

-- =============================================================================
-- End of R001. No foods, ingredients, recipes, or user rows are seeded:
-- catalog content is curated through the backend with per-row provenance, and
-- user data is created only by real registration.
-- =============================================================================

