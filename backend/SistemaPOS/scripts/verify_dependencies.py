"""Check Maven runtime dependencies against OSV; exit 1 on advisories, 2 on scan failure."""
import argparse
import json
import pathlib
import re
import sys
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', default='target/dependencies-audit.txt')
    parser.add_argument('--output', default='target/osv-audit.json')
    args = parser.parse_args()
    dependencies = sorted(set(re.findall(
        r'^\s+([^\s:]+):([^\s:]+):jar:([^\s:]+):(compile|runtime)\b',
        pathlib.Path(args.inventory).read_text(encoding='utf-8'), re.MULTILINE)))
    if not dependencies:
        raise ValueError('Empty inventory; generate it with Maven dependency:list first')
    queries = [{'package': {'ecosystem': 'Maven', 'name': f'{group}:{artifact}'}, 'version': version}
               for group, artifact, version, _ in dependencies]
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
        data=json.dumps({'queries': queries}).encode(),
        headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=60) as response:
        results = json.load(response)['results']
    if len(results) != len(dependencies):
        raise ValueError('Incomplete response from OSV')
    findings = [{'package': query['package']['name'], 'version': query['version'],
                 'advisories': result['vulns']} for query, result in zip(queries, results)
                if result.get('vulns')]
    report = {'source': 'https://api.osv.dev', 'dependencies': len(dependencies), 'findings': findings}
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"OSV: {len(dependencies)} runtime dependencies, {len(findings)} affected packages")
    for finding in findings:
        print(f"{finding['package']}:{finding['version']}: " + ', '.join(v['id'] for v in finding['advisories']))
    return 1 if findings else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'Dependency scan failed: {error}', file=sys.stderr)
        sys.exit(2)
