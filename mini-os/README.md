# Mini OS — OS Lab (UMak)

Pixel-art OS simulator with 8 modules. Built in Java Swing.

## Run
Open the folder in VS Code (Extension Pack for Java) → run `src/app/Main.java`.
Always run from the project root so `assets/` and `fonts/` are found.

## Who does what
| Member | Modules | Folders |
|---|---|---|
| Member 1 | CPU Scheduling, Paging Lab (done — example), Segmentation | `src/modules/cpu`, `paging`, `segmentation` |
| Member 2 | Disk Scheduling, Page Replacement, Multiprogramming | `src/modules/disk`, `replacement`, `multiprogramming` |
| Member 3 | Banker's Algorithm, Fragmentation + app shell | `src/modules/banker`, `fragmentation`, `src/app`, `src/shared` |

Each module file lists its TODOs and the answer to check against.

## Rules
- Only edit your own folders. Ask Member 3 for changes in `src/app` or `src/shared`.
- Never push to `main` — use a branch + Pull Request.
- Pull before you start, pull `main` again before you push.
- Only push code that runs.

## Daily Git
```
git checkout main
git pull
git checkout -b module-task        # e.g. cpu-gantt-chart
# ...work...
git add .
git commit -m "CPU: add SJF"
git pull origin main
git push -u origin module-task     # then open a Pull Request on GitHub
```

## Fonts & images
`fonts/` — Press Start 2P, Silkscreen, DotGothic16, Pixelify Sans, Nunito (SIL Open Font License, see LICENSE-OFL.txt) and Yellowtail (Apache 2.0).
`assets/images/` — wallpapers, jeep, heron, folders, app icon.
