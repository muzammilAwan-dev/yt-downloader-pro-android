"""Stdlib-only checks of Room's exported DDL/DAO SQL, not Android Room instrumentation."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "app/schemas/io.github.muzammilawan.clipharbor.download.TaskDatabase/1.json"
DAO = ROOT / "app/src/main/java/io/github/muzammilawan/clipharbor/download/RoomTaskStore.kt"


class TaskSchemaTest(unittest.TestCase):
    def setUp(self):
        self.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        entity, = self.schema["entities"]
        self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        self.queries = re.findall(r'@Query\("([^"\n]+)"\)', DAO.read_text(encoding="utf-8"))

    def insert(self, ident="one", created=10):
        self.db.execute("INSERT INTO tasks(id,sourceUrl,phase,createdAt,updatedAt,revision) VALUES(?,?,?,?,?,?)",
                        (ident, "https://example.invalid/watch?v=test", "STAGED", created, created, 0))
        self.db.commit()

    def test_exact_schema_has_no_sensitive_payload_columns(self):
        names = {row[1] for row in self.db.execute("PRAGMA table_info(tasks)")}
        self.assertEqual(names, {"id", "sourceUrl", "title", "phase", "videoHeight", "audioFormatId",
                                 "videoFormatId", "reason", "savedUri", "savedName", "savedMime",
                                 "createdAt", "updatedAt", "revision"})
        self.assertEqual(self.schema["version"], 1)

    def test_exported_schema_reopens_committed_records(self):
        self.insert()
        copy = sqlite3.connect(":memory:")
        self.addCleanup(copy.close)
        self.db.backup(copy)
        self.assertEqual(copy.execute("SELECT id,phase,revision FROM tasks").fetchall(), [("one", "STAGED", 0)])

    def test_actual_dao_queries_order_and_revision_fence_deletion(self):
        self.insert("older", 1)
        self.insert("newer", 2)
        read, all_rows, get, delete = self.queries
        self.assertEqual([r[0] for r in self.db.execute(read)], ["newer", "older"])
        self.assertEqual([r[0] for r in self.db.execute(all_rows)], ["older", "newer"])
        self.assertEqual(self.db.execute(get, {"id": "older"}).fetchone()[0], "older")
        self.assertEqual(self.db.execute(delete, {"id": "older", "revision": 1}).rowcount, 0)
        self.assertEqual(self.db.execute(delete, {"id": "older", "revision": 0}).rowcount, 1)

    def test_primary_key_and_transaction_rollback(self):
        self.insert()
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert()
        self.db.rollback()
        with self.assertRaises(RuntimeError):
            with self.db:
                self.db.execute("UPDATE tasks SET phase='PUBLISHING',revision=revision+1 WHERE id='one'")
                raise RuntimeError("synthetic process boundary")
        self.assertEqual(self.db.execute("SELECT phase,revision FROM tasks").fetchone(), ("STAGED", 0))


if __name__ == "__main__":
    unittest.main()
