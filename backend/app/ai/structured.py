"""Provider-only closed schema. Public DTOs and persisted interpretation v1 stay unchanged."""
import copy
import json


def strict_schema(model) -> dict:
    schema = copy.deepcopy(model.model_json_schema())

    def close(node):
        if isinstance(node, list):
            for child in node: close(child)
        elif isinstance(node, dict):
            node.pop("default", None)
            properties = node.get("properties")
            if properties is not None:
                for name in ("fields", "evidence"):
                    if name in properties:
                        # Open-ended dictionaries are not supported by strict output. Preserve
                        # arbitrary registered field primitives with a bounded key/value wire list.
                        properties[name] = {"type": "array", "maxItems": 32 if name == "fields" else 10, "items": {
                            "type": "object", "properties": {"key": {"type": "string"}, "value": {"type": "string", "description": "JSON-encoded value" if name == "fields" else "Exact original-text quote"}},
                            "required": ["key", "value"], "additionalProperties": False}}
                node["required"] = list(properties)
                node["additionalProperties"] = False
            for child in list(node.values()): close(child)
    close(schema)
    return schema


def decode_strict(payload: dict) -> dict:
    for record in payload.get("records", []):
        for name in ("fields", "evidence"):
            entries = record.get(name)
            if not isinstance(entries, list):
                raise ValueError("Strict output must use key/value lists")
            values = {}
            for entry in entries:
                if set(entry) != {"key", "value"} or entry["key"] in values:
                    raise ValueError("Invalid or duplicate provider field")
                values[entry["key"]] = json.loads(entry["value"]) if name == "fields" else entry["value"]
            record[name] = values
    return payload
