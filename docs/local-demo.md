# Windows local demo

Run these commands in PowerShell from the repository root. Start Docker Desktop
first, with Linux containers enabled. Install Java, Maven, Flutter with Chrome,
and a Python virtual environment at `.venv` or `ai_service/.venv` containing
`ai_service[vision]`. The script checks that these commands are available.

```powershell
.\scripts\local\start-demo.ps1
Write-Host "TERMINAL_INPUT_OK"
.\scripts\local\status-demo.ps1
.\scripts\local\stop-demo.ps1
```

`start-demo.ps1` prompts without echoing for `DB_PASSWORD` and
`AI_INTERNAL_SERVICE_TOKEN` when they are absent from the process environment.
The AI token must be at least 32 characters. For unattended startup, supply
both environment variables first and run `start-demo.ps1 -NoPrompt`. Use the
same token on a later start if either script-owned application service is still
running. Do not place secrets in command arguments or committed files.

The script creates or reuses MySQL 8 on host port 3307 and Mailpit on ports
1025/8025. A new MySQL container uses database `smart_meal_planner`, user
`smartmeal`, the supplied `DB_PASSWORD`, and a generated local root password.
It verifies SQL access with that user before starting the apps. Existing
containers must already have matching database credentials. It starts FastAPI
on 127.0.0.1:8000 with `AI_VISION_DEVICE=0` by default and
`AI_RECOGNITION_CONFIDENCE=0.35`, Spring Boot with the `local` profile on
port 8080, and Flutter Web on port 3000. Open <http://localhost:3000> and
Mailpit at <http://localhost:8025>.

## Explicit demo catalog import

Normal startup does not seed food or recipes. After the local MySQL database is
running, set `DB_URL` (for example `jdbc:mysql://localhost:3307/smart_meal_planner`),
`DB_USERNAME`, and `DB_PASSWORD` in the importing PowerShell session, then run:

```powershell
.\scripts\local\import-demo-data.ps1
```

The command regenerates `database/demo/catalog-v1.json` and
`database/demo/recipes-v1.json`, then runs the existing catalog and recipe
importers with explicit import profiles. It accepts only a localhost MySQL URL.
The versioned demo dataset has 16 Foods, 16 Ingredients (including all 12
recognition classes), and 18 Recipes. Its nutrient values are **estimated demo
values**, not verified food composition data. Repeating the import uses stable
source identities. Verify the authenticated `/api/v1/foods`,
`/api/v1/ingredients`, and `/api/v1/recipes` pages have nonzero
`totalElements` before planning.

The scripts keep process and container IDs under the ignored `.local-demo/`
directory. Output is in `.local-demo/logs/`. `stop-demo.ps1` stops only processes
whose recorded PID and start time still match its worker process, plus
containers whose recorded IDs still match. Containers that were already
running before `start-demo.ps1` remain running. MySQL data in a script-created
container persists across stop/start; `stop-demo.ps1` never deletes containers
or volumes. Each background worker receives an empty, closed standard input
stream so Flutter cannot consume keystrokes from the launching PowerShell prompt.

## Troubleshooting

- **Docker unavailable:** start Docker Desktop and confirm `docker ps` works in
  the same PowerShell session. The scripts do not start Docker Desktop.
- **Port occupied:** read the PID in the startup error, or run
  `Get-NetTCPConnection -State Listen -LocalPort 3000,8000,8080,3307,1025,8025`.
  The scripts leave unexpected processes alone.
- **MySQL timeout:** confirm the supplied `DB_PASSWORD` matches the existing
  `smartmeal` account. Check `docker logs smartmeal-demo-mysql` if that is the
  container name. A new container also needs time to initialize Flyway's
  database before Spring becomes healthy.
- **FastAPI or Spring timeout:** inspect `.local-demo/logs/ai.err.log` or
  `.local-demo/logs/backend.err.log`, then run `status-demo.ps1`.
- **Flutter timeout:** inspect `.local-demo/logs/flutter.err.log` and check
  that Chrome is available to Flutter (`flutter devices`).
- **YOLO first request:** `/health` does not load the model. The first image
  inference can be slower while YOLO loads the checkpoint and initializes the
  GPU. No generic image warm-up is included because it would need a suitable
  local image and an authenticated inference request.
