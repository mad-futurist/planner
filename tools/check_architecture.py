"""Executable source dependency rules for the two existing monoliths."""
import ast
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def violations(root=ROOT):
    errors = []
    backend = root / "backend/app"
    forbidden = {
        "domain": ("fastapi", "pydantic", "sqlalchemy", "httpx", "app.application", "app.infrastructure", "app.api", "app.persistence", "app.schemas", "app.security", "app.ai"),
        "application": ("fastapi", "pydantic", "sqlalchemy", "httpx", "app.infrastructure", "app.api", "app.persistence", "app.schemas", "app.security", "app.ai", "app.config"),
        "api": ("sqlalchemy", "app.persistence", "app.services", "app.security", "app.ai", "app.infrastructure"),
    }
    for layer, blocked in forbidden.items():
        for path in (backend / layer).rglob("*.py"):
            for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
                modules = []
                if isinstance(node, ast.Import):
                    modules = [alias.name for alias in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module:
                    modules = [node.module]
                for module in modules:
                    if any(module == name or module.startswith(name + ".") for name in blocked):
                        errors.append(f"{path.relative_to(root)}:{node.lineno}: {layer} imports {module}")
    android = root / "android/app/src/main/java/app/planner"
    allowed_roots = {"domain", "application", "infrastructure", "presentation"}
    entry_points = {"MainActivity.kt", "PlannerApp.kt"}
    for path in android.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(android)
        if len(relative.parts) == 1:
            if relative.name not in entry_points:
                errors.append(f"{path.relative_to(root)}: root is reserved for Android entry points")
        elif relative.parts[0] not in allowed_roots:
            errors.append(f"{path.relative_to(root)}: use a canonical architecture package")
        expected = "app.planner" + ("." + ".".join(relative.parts[:-1]) if len(relative.parts) > 1 else "")
        declared = re.search(r"^package\s+(\S+)", source, re.M)
        if not declared or declared[1] != expected:
            errors.append(f"{path.relative_to(root)}: package must match source directory {expected}")
        if relative.parts[0] == "presentation" and len(relative.parts) < 3:
            errors.append(f"{path.relative_to(root)}: presentation files need a feature/shared owner")
        if relative.parts[0] == "application" and (len(relative.parts) < 3 or relative.parts[1] not in {"port", "usecase", "model"}):
            errors.append(f"{path.relative_to(root)}: application files belong in port, usecase or model")
        if "EntryPointAccessors.fromApplication" in source:
            errors.append(f"{path.relative_to(root)}: runtime service lookup bypasses constructor injection")
    for layer, blocked in {
        "domain": ("android", "androidx", "retrofit", "com.google.gson", "app.planner.application", "app.planner.infrastructure", "app.planner.presentation", "app.planner.core", "app.planner.data", "app.planner.features", "app.planner.sync", "app.planner.navigation"),
        "application": ("android", "androidx", "retrofit", "com.google.gson", "app.planner.infrastructure", "app.planner.presentation", "app.planner.core", "app.planner.data", "app.planner.features", "app.planner.sync", "app.planner.navigation"),
        "presentation": ("app.planner.infrastructure", "app.planner.core", "app.planner.data", "app.planner.sync", "app.planner.features", "app.planner.navigation"),
    }.items():
        for path in (android / layer).rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            for line, value in enumerate(text.splitlines(), 1):
                imported = re.match(r"import\s+(\S+)", value)
                if imported and any(imported[1] == name or imported[1].startswith(name + ".") for name in blocked):
                    platform_binding = path.relative_to(android).as_posix() == "presentation/settings/platform/OverlaySettingsRoute.kt"
                    if not (platform_binding and imported[1].startswith("app.planner.infrastructure.overlay.")):
                        errors.append(f"{path.relative_to(root)}:{line}: {layer} imports {imported[1]}")
                if layer == "presentation" and re.search(r"\bval\s+\w+\s*=\s*MutableStateFlow", value) and not re.search(r"\b(private|protected)\b", value):
                    errors.append(f"{path.relative_to(root)}:{line}: public mutable UI state")
                if layer == "presentation" and (".repository.dao" in value or "vm.action" in value):
                    errors.append(f"{path.relative_to(root)}:{line}: UI bypasses application commands")
                if layer == "presentation" and "NavHostController" in value and path.name not in {"PlannerNavHost.kt", "PlannerUI.kt"}:
                    errors.append(f"{path.relative_to(root)}:{line}: screen content receives a navigation controller")
                if path.name == "PlannerNavHost.kt" and "hiltViewModel" in value:
                    errors.append(f"{path.relative_to(root)}:{line}: feature Route owns ViewModel creation")
    return errors


if __name__ == "__main__":
    found = violations()
    print("\n".join(found) if found else "Architecture boundaries: OK")
    raise SystemExit(bool(found))
