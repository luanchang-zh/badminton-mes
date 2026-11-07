"""pytest 根配置：把 lightrag-service 加入导入路径，使 tests 可直接 import tenant。"""

import sys
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))
