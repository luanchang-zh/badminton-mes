"""隔离验证入口。

只是一个薄封装：把 scripts 目录加入导入路径后转交 runner.main()。
单独留这个文件是为了保持文档里的命令简短。
"""

from __future__ import annotations

import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def main():
    """转交命令行入口。"""
    from isolation.runner import main as runner_main

    return runner_main()


if __name__ == "__main__":
    sys.exit(main())
