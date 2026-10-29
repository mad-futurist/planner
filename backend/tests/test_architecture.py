import importlib.util
from pathlib import Path


def test_source_dependencies_point_inward():
    path = Path(__file__).resolve().parents[2] / "tools/check_architecture.py"
    spec = importlib.util.spec_from_file_location("architecture_check", path)
    check = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(check)
    assert check.violations() == []
