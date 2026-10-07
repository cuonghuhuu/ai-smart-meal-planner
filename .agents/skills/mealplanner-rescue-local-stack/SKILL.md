---
name: mealplanner-rescue-local-stack
description: Stabilize and run the AI Smart Meal Planner rescue build locally across MySQL, Spring Boot, FastAPI, and Flutter. Use for local demo readiness, health checks, and integration debugging.
---
# Meal Planner Rescue Local Stack

## Objective

Get the rescue branch to a repeatable local-demo state without unnecessary deployment work.

## Architecture

Flutter Android/Web -> Spring Boot -> MySQL
                         |
                         -> FastAPI -> Meal Planning / Ranking / YOLO

Python must not access the application database directly.

## Workflow

1. Preserve the rescue branch and avoid unrelated refactors.
2. Start/check MySQL.
3. Start Spring Boot and verify its health/API.
4. Start FastAPI and verify /health.
5. Start Flutter Web first for fastest feedback, then Android when needed.
6. Verify one end-to-end happy path before polishing:
   profile -> pantry -> recognition/recommendation -> meal plan -> shopping list.
7. For every failure, isolate the boundary first: client, Java, DB, or Python.
8. Run focused tests before full suites when time is critical.
9. Never disable auth/security merely to make the demo pass.

## Demo gate

A build is demo-ready only when:
- services restart cleanly,
- health checks are green,
- seed/demo data is deterministic,
- primary AI flow works without manual DB edits,
- known fallback behavior is documented.
