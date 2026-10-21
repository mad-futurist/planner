from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.config import settings


def make_database(url: str):
    kwargs = {"pool_pre_ping": True}
    if url.startswith("postgresql"):
        kwargs["connect_args"] = {"connect_timeout": 10}
    if url.startswith("sqlite"):
        kwargs["connect_args"] = {"check_same_thread": False}
        if ":memory:" in url:
            kwargs["poolclass"] = StaticPool
    engine = create_engine(url, **kwargs)
    return engine, sessionmaker(engine, expire_on_commit=False)


engine, SessionFactory = make_database(settings.database_url)
