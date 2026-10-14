#!/usr/bin/env python3
"""Clearly labelled synthetic fixtures, never definitive JARVIS knowledge."""
import argparse
import json
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--output-dir', type=Path, default=Path('build/catalogue-fixtures'))
parser.add_argument('--count', type=int, default=1500)
args = parser.parse_args()
if not 1001 <= args.count <= 4500:
    parser.error('count must be between 1001 and 4500 (leave room for existing catalogue entries)')
args.output_dir.mkdir(parents=True, exist_ok=True)
def record(identifier, intent, title, examples, variants):
    return dict(id=identifier, intent=intent, title=title, category='TEST SYNTHÉTIQUE', revision=1,
                examples=examples, variants=variants, origin='SYNTHETIC', deleted=False)
def save(name, records):
    (args.output_dir/name).write_text(json.dumps(dict(formatVersion=1,answers=records),ensure_ascii=False,indent=2)+'\n')
save(f'catalogue-synthetic-{args.count}.json',[
    record(f'synthetic.{n}',f'synthetic.{n}',f'TEST SYNTHÉTIQUE {n}',[f'Test synthétique référence {n}'],[f'Donnée synthétique {n}, sans valeur métier.']) for n in range(args.count)])
save('catalogue-conflicts-test.json',[
    record('synthetic.conflict.a','synthetic.conflict','TEST : réponse contradictoire A',['Le service de test est-il disponible ?'],['Le service de test est disponible.']),
    record('synthetic.conflict.b','synthetic.conflict','TEST : réponse contradictoire B',['Le service de test est-il disponible ?'],['Le service de test est indisponible.'])])
save('catalogue-variants-test.json',[
    record('synthetic.variants','synthetic.variants','TEST : variantes',['Donne une réponse de démonstration'],['Voici une réponse locale de démonstration.','Ceci est une autre formulation de la réponse locale de démonstration.'])])
print(f'Synthetic fixtures only: {args.output_dir}')
