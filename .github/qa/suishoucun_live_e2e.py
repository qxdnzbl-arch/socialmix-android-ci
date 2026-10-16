import base64, json, secrets, urllib.request, uuid
URL = "https://suishoucun-sync.floot.app/_api/sync"
AUTH = secrets.token_urlsafe(32)
NAME = "manifest/" + uuid.uuid4().hex
EXPECTED = b"suishoucun-smoke-" + uuid.uuid4().hex.encode()

def rpc(action, **kwargs):
    body = json.dumps({"action": action, **kwargs}).encode()
    req = urllib.request.Request(URL, data=body, method="POST", headers={
        "Content-Type":"application/json", "Accept":"application/json",
        "x-sync-auth":AUTH, "User-Agent":"Suishoucun-Android"})
    with urllib.request.urlopen(req, timeout=35) as r:
        result = json.loads(r.read(1024*1024))
        assert isinstance(result, dict), (action, result)
        return result

head = rpc("suishoucun_sync_head_v2")
assert head.get("revision", 0) == 0, ("Synthetic identity was not empty", head)
try:
    rpc("suishoucun_sync_prepare_object_v2", p_object=NAME, p_chunk_count=1)
    rpc("suishoucun_sync_put_batch_v3", p_object=NAME, p_chunk_count=1,
        p_chunks=[{"index":0,"payload":base64.b64encode(EXPECTED).decode()}])
    status = rpc("suishoucun_sync_object_status_v2", p_object=NAME)
    assert status.get("complete") is True, ("status", status)
    received = rpc("suishoucun_sync_get_batch_v3", p_object=NAME, p_indices=[0])
    assert received.get("found") is True and received.get("chunks") == 1, received
    assert base64.b64decode(received["parts"][0]["payload"]) == EXPECTED
    commit = rpc("suishoucun_sync_commit_v2", p_expected=0, p_manifest=NAME)
    assert commit.get("ok") is True and commit.get("revision") == 1, commit
    latest = rpc("suishoucun_sync_head_v2")
    assert latest.get("revision") == 1 and latest.get("manifest") == NAME, latest
    print("PASS: isolated test identity upload, read, commit and read back")
finally:
    deleted = rpc("suishoucun_sync_delete_v2")
    assert deleted.get("ok") is True, ("CLEANUP FAILED", deleted)
print("PASS: isolated test identity removed, no real user data touched")
