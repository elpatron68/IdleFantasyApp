import argparse

from docs.gen.repo_docs.generate_docs import get_docs


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def run_update():
    for path, content in get_docs().items():
        path.write_text(content, encoding="utf-8")


def parse_args():
    parser = argparse.ArgumentParser(description="Idle Fantasy document generators.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("update", help="Update all documentation and metadata in the repository.")

    args = parser.parse_args()

    return args


def main():
    args = parse_args()
    if args.command == "update":
        run_update()
    else:
        raise NotImplementedError


if __name__ == "__main__":
    main()