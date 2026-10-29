"""Identical bounded context and approved rules for durable jobs and explicit AI preview."""
import re
from app.application.values import TypeDTO

def relevant_corrections(db, note):
    words = set(re.findall(r"\w+", note.content.casefold()))
    recent = db.context_corrections(note.user_id)
    def relevance(c): return len(words & set(re.findall(r"\w+", (str(c.previous_interpretation) + str(c.corrected_interpretation)).casefold())))
    ranked = sorted((c for c in recent if relevance(c) > 0), key=relevance, reverse=True)
    return [{"before": c.previous_interpretation, "after": c.corrected_interpretation} for c in ranked[:5]]

def context_for(db, note):
    rows = db.context_types(note.user_id)
    active = {row.id: TypeDTO.from_mapping(row.data) for row in rows}
    rules = [r.data for r in db.context_rules(note.user_id)]
    context = {"capturedAt": note.created_at, "timezone": note.timezone, "locale": note.locale, "types": [t.to_dict(mode="json") for t in active.values()], "rules": rules, "corrections": relevant_corrections(db, note), "interpretationHints": note.interpretation_hints or {}}
    return active, rules, context

def apply_rules(extraction, rules, content):
    changes = {key: value for rule in rules if rule["conditions"]["contains"].casefold() in content.casefold() for key, value in rule["action"].items()}
    return extraction.replace(update={"records": [proposal.replace(update=changes) for proposal in extraction.records]})
