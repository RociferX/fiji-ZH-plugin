"""Download the official Windows x64 Fiji Latest ZIP using resumable ranges."""
import concurrent.futures
import hashlib
import json
import pathlib
import subprocess
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = 'https://downloads.micron.ox.ac.uk/fiji_update/mirrors/fiji-latest/fiji-latest-win64-jdk.zip'
DEST = ROOT / 'downloads' / 'fiji-latest-win64-jdk.zip'


def main():
    with urllib.request.urlopen(urllib.request.Request(URL, method='HEAD'), timeout=40) as response:
        size = int(response.headers['Content-Length'])
        modified = response.headers.get('Last-Modified')
        etag = response.headers.get('ETag')
    chunk = 4 * 1024 * 1024
    parts = DEST.parent / ('parts-' + etag.replace('"', ''))
    parts.mkdir(parents=True, exist_ok=True)
    count = (size + chunk - 1) // chunk
    started = time.monotonic()
    print(f'Official mirror: {URL}\nBytes: {size}; Last-Modified: {modified}; chunks: {count}', flush=True)

    def download(index):
        start, end = index * chunk, min(size, (index + 1) * chunk) - 1
        path = parts / f'{index:04}.part'
        if path.exists() and path.stat().st_size == end - start + 1:
            return index
        for attempt in range(5):
            try:
                header = path.with_suffix('.headers')
                offset = path.stat().st_size if path.exists() else 0
                remaining = path.with_suffix('.pending')
                request_start = start + offset
                result = subprocess.run(['curl.exe', '-fsSL', '--max-time', '60', '--connect-timeout', '15',
                    '--range', f'{request_start}-{end}', '-H', f'If-Range: {etag}', '-D', str(header),
                    '-o', str(remaining), URL], capture_output=True)
                headers = header.read_text(encoding='iso-8859-1').lower()
                if f'content-range: bytes {request_start}-{end}/{size}' not in headers:
                    raise RuntimeError('Server did not honor the requested range or the archive changed')
                data = remaining.read_bytes()
                if offset + len(data) > end - start + 1:
                    raise RuntimeError('Unexpected chunk size')
                with path.open('ab') as output:
                    output.write(data)
                if result.returncode:
                    raise RuntimeError(result.stderr.decode(errors='replace').strip())
                if path.stat().st_size != end - start + 1:
                    raise RuntimeError('Incomplete chunk')
                return index
            except Exception:
                if attempt == 4:
                    raise
                time.sleep(attempt + 1)

    failures = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        for completed, future in enumerate(concurrent.futures.as_completed([pool.submit(download, i) for i in range(count)]), 1):
            try:
                future.result()
            except Exception as error:
                failures.append(str(error))
                print(f'Chunk failed: {error}', flush=True)
            if completed % 8 == 0 or completed == count:
                print(f'{completed}/{count} chunks ({completed/count:.0%}), elapsed {time.monotonic()-started:.0f}s', flush=True)
    if failures:
        raise RuntimeError(f'{len(failures)} chunk(s) failed; rerun to resume. First error: {failures[0]}')
    digest = hashlib.sha256()
    with DEST.open('wb') as output:
        for index in range(count):
            data = (parts / f'{index:04}.part').read_bytes()
            output.write(data)
            digest.update(data)
    record = {'source': URL, 'bytes': size, 'last_modified': modified, 'etag': etag, 'sha256': digest.hexdigest()}
    (DEST.parent / 'fiji-download.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print(json.dumps(record, indent=2), flush=True)


if __name__ == '__main__':
    main()
