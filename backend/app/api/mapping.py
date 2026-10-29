from app.application import values

def _provided_fields(wire, value):
    if isinstance(value, values.Value):
        value._provided = frozenset(wire.model_fields_set)
        for name in type(wire).model_fields:
            _provided_fields(getattr(wire, name), getattr(value, name))
    elif isinstance(value, list):
        for original, mapped in zip(wire, value):
            _provided_fields(original, mapped)
    elif isinstance(value, dict):
        for key, mapped in value.items():
            _provided_fields(wire[key], mapped)

def command(dto):
    cls = getattr(values, type(dto).__name__)
    # Keep evaluated defaults (notably timestamps), and separately preserve which
    # fields were supplied so existing idempotency fingerprints remain identical.
    result = cls.from_mapping(dto.model_dump())
    _provided_fields(dto, result)
    return result
