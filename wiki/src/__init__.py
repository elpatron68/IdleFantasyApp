from pathlib import Path

WIKI_ROOT   = Path(__file__).parents[1]
REPO_ROOT   = WIKI_ROOT.parent
ASSETS      = REPO_ROOT / "app" / "src" / "main" / "assets" / "data"
SPRITES     = REPO_ROOT / "app" / "src" / "main" / "assets" / "sprites"
RESOURCES   = REPO_ROOT / "app" / "src" / "main" / "res"
TEMPLATES   = WIKI_ROOT / "templates"
GITHUB_REPO = "https://github.com/tristinbaker/IdleFantasy"