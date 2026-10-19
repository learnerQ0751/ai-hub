$ErrorActionPreference = "Stop"

Write-Host "1/3 validate rules..."
python standards/generator/validate.py

Write-Host "2/3 regenerate artifacts..."
python standards/generator/generate.py

Write-Host "3/3 run quality gates..."
mvn -Pquality verify
