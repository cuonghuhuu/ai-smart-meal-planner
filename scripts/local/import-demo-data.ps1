$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

if (-not $env:DB_URL -or -not $env:DB_USERNAME -or -not $env:DB_PASSWORD) {
    throw 'Set DB_URL, DB_USERNAME, and DB_PASSWORD for the local MySQL database before import.'
}
if ($env:DB_URL -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1)(:\d+)?/') {
    throw 'Demo import accepts only a localhost MySQL DB_URL.'
}

python (Join-Path $PSScriptRoot 'build-demo-data.py')
if ($LASTEXITCODE -ne 0) { throw 'Demo dataset generation failed.' }

$previousCatalog = $env:CATALOG_IMPORT_SOURCE_FILE
$previousRecipe = $env:RECIPE_IMPORT_SOURCE_FILE
try {
    $env:CATALOG_IMPORT_SOURCE_FILE = (Resolve-Path (Join-Path $repoRoot 'database/demo/catalog-v1.json')).Path
    mvn -f (Join-Path $repoRoot 'backend/pom.xml') spring-boot:run `
        '-Dspring-boot.run.profiles=local,catalog-import' `
        '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --app.catalog.import.enabled=true'
    if ($LASTEXITCODE -ne 0) { throw 'Catalog import failed; recipe import was skipped.' }

    $env:RECIPE_IMPORT_SOURCE_FILE = (Resolve-Path (Join-Path $repoRoot 'database/demo/recipes-v1.json')).Path
    mvn -f (Join-Path $repoRoot 'backend/pom.xml') spring-boot:run `
        '-Dspring-boot.run.profiles=local,recipe-import' `
        '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --app.recipe.import.enabled=true'
    if ($LASTEXITCODE -ne 0) { throw 'Recipe import failed.' }
} finally {
    $env:CATALOG_IMPORT_SOURCE_FILE = $previousCatalog
    $env:RECIPE_IMPORT_SOURCE_FILE = $previousRecipe
}
