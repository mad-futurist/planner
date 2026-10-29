import importlib.util
from pathlib import Path
import pytest


def test_source_dependencies_point_inward():
    path = Path(__file__).resolve().parents[2] / "tools/check_architecture.py"
    spec = importlib.util.spec_from_file_location("architecture_check", path)
    check = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(check)
    assert check.violations() == []


@pytest.mark.parametrize("relative,source,reason", [
    ("features/TodayViewModel.kt", "package app.planner.features", "canonical architecture package"),
    ("presentation/TasksScreen.kt", "package app.planner.presentation", "feature/shared owner"),
    ("presentation/tasks/TasksViewModel.kt", "package app.planner.presentation.tasks\nimport app.planner.infrastructure.repository.PlannerRepository", "presentation imports"),
    ("presentation/tasks/TasksScreen.kt", "package app.planner.features", "package must match"),
    ("application/Capture.kt", "package app.planner.application", "application files belong"),
    ("domain/model/Task.kt", "package app.planner.domain.model\nimport app.planner.infrastructure.database.PlannerDatabase", "domain imports"),
    ("presentation/navigation/PlannerNavHost.kt", "package app.planner.presentation.navigation\nimport androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel", "feature Route owns"),
])
def test_android_layout_rejects_regressions(tmp_path, relative, source, reason):
    path = Path(__file__).resolve().parents[2] / "tools/check_architecture.py"
    spec = importlib.util.spec_from_file_location("architecture_check", path)
    check = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(check)
    example = tmp_path / "android/app/src/main/java/app/planner" / relative
    example.parent.mkdir(parents=True)
    example.write_text(source, encoding="utf-8")
    assert any(reason in error for error in check.violations(tmp_path))


def test_only_overlay_platform_binding_can_import_native_overlay(tmp_path):
    path = Path(__file__).resolve().parents[2] / "tools/check_architecture.py"
    spec = importlib.util.spec_from_file_location("architecture_check", path)
    check = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(check)
    example = tmp_path / "android/app/src/main/java/app/planner/presentation/settings/platform/OverlaySettingsRoute.kt"
    example.parent.mkdir(parents=True)
    example.write_text("package app.planner.presentation.settings.platform\nimport app.planner.infrastructure.overlay.OverlayManager", encoding="utf-8")
    assert check.violations(tmp_path) == []
    example.write_text(example.read_text() + "\nimport app.planner.infrastructure.repository.PlannerRepository", encoding="utf-8")
    assert any("presentation imports" in error for error in check.violations(tmp_path))
