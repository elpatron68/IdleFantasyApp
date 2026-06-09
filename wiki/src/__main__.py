#!/usr/bin/env python3
"""
__main__.py (wiki.src) — Auto-generate IdleFantasy GitHub Wiki pages from game data assets.

Usage:
    cd wiki
    python3 -m src -h

Reads JSON assets from app/src/main/assets/data/ and generates markdown pages.
Provides validation, generation and automatic GitHub updating.
"""
import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path

from wiki.src import REPO_ROOT
from wiki.src.pages import get_pages, check_wiki_validity

WIKI_REPO  = "git@github.com:tristinbaker/IdleFantasy.wiki.git"
WIKI_DIR = REPO_ROOT / "out" / "IdleFantasy-wiki"

# ---------------------------------------------------------------------------
# Wiki repo management
# ---------------------------------------------------------------------------


def clone_wiki(wiki_dir: Path):
    if wiki_dir.exists():
        shutil.rmtree(wiki_dir)
    subprocess.run(["git", "clone", WIKI_REPO, str(wiki_dir)], check=True)


def commit_and_push(wiki_dir: Path):
    subprocess.run(["git", "-C", str(wiki_dir), "add", "-A"], check=True)
    result = subprocess.run(
        ["git", "-C", str(wiki_dir), "diff", "--cached", "--quiet"]
    )
    if result.returncode == 0:
        print("Wiki is already up to date — nothing to push.")
        return
    subprocess.run(
        ["git", "-C", str(wiki_dir), "commit", "-m", "Auto-update wiki from game data"],
        check=True,
    )
    subprocess.run(["git", "-C", str(wiki_dir), "push"], check=True)


# ---------------------------------------------------------------------------
# Page management
# ---------------------------------------------------------------------------


def write_pages(out: Path, pages: dict[str, str]):
    for filename, content in pages.items():
        (out / filename).write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def run_update():
    with tempfile.TemporaryDirectory() as tmpdir:
        pages = get_pages()
        print(f"Generated {len(pages)} pages.")

        clone_wiki(Path(tmpdir))
        write_pages(WIKI_DIR, pages)
        commit_and_push(Path(tmpdir))
        print("Done.")


def run_write(out: Path, page_list: str | list[str]):
    # Create page content
    pages = get_pages()
    # Reset output directory
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    # Write all pages or just specific pages
    if page_list == "all":
        write_pages(out, pages)
    else:
        write_pages(out, {k: v for k, v in pages if k in page_list})


def parse_args():
    parser = argparse.ArgumentParser(description="Idle Fantasy wiki tools.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("update", help="Clone the wiki repo, generate pages, and push.")
    subparsers.add_parser("validity", help="Check wiki page configuration for errors.")

    write_parser = subparsers.add_parser("write", help="Write generated pages to a local directory.")
    write_parser.add_argument(
        "-d",
        "--wiki-dir",
        type=Path,
        default=WIKI_DIR,
        help=f"Output directory for wiki pages (default: {WIKI_DIR}).",
    )
    write_parser.add_argument(
        "-p",
        "--pages",
        nargs="*",
        default="all",
        metavar="PAGE",
        help='Page IDs to write, or "all" (default: all).',
    )

    args = parser.parse_args()

    if args.command == "write" and isinstance(args.pages, list):
        if not args.pages or args.pages == ["all"]:
            args.pages = "all"

    return args


def main():
    args = parse_args()
    if args.command == "update":
        run_update()
    elif args.command == "validity":
        check_wiki_validity()
    elif args.command == "write":
        run_write(WIKI_DIR, args.pages)
    else:
        raise NotImplementedError


if __name__ == "__main__":
    main()
