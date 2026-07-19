.PHONY: install test lint analyze eval api

install:
	pip install -e ".[api,dev]"

test:
	pytest

lint:
	ruff check analyzer api eval

# Demo: analyze a synthetic film and resolve all three levels from one pass.
analyze:
	python -m analyzer analyze --synthetic --duration 360 --level L2 -o analysis.json
	python -m analyzer segments analysis.json --level L1
	python -m analyzer segments analysis.json --level L3

eval:
	python -m eval.harness tests/fixtures/golden_mini.json --level L3

api:
	uvicorn api.main:app --reload
