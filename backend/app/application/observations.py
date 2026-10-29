"""Pure immutable input projection for observation ports."""
def snapshot(note):
    return {"noteId": getattr(note, "id", None), "inputVersion": getattr(note, "version", None),
            "content": note.content, "originalContent": getattr(note, "original_content", note.content),
            "capturedAt": note.created_at, "timezone": note.timezone, "locale": note.locale,
            "interpretationHints": note.interpretation_hints or {}}
