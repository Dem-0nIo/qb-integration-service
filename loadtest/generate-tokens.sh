#!/bin/bash
# generate-tokens.sh — construye tokens.json llamando al endpoint temporal
# /api/dev/generate-token, evitando cualquier copiado manual del secreto.
#
# IMPORTANTE: este script depende de DevTokenController, que es un endpoint
# TEMPORAL solo para ambiente local de desarrollo. Nunca debe existir en
# producción — ver checklist de Fase 5 / pre-producción.
#
# IMPORTANTE (datos): los pares email:qbEmpresa de abajo son PLACEHOLDERS.
# Reemplázalos localmente con datos reales tomados de customer_mapping /
# customers para correr las pruebas — pero NUNCA commitees ese reemplazo.
# Si necesitas guardar la lista real en disco, usa un archivo separado
# (ej. loadtest/clientes-reales.txt) que esté en .gitignore.
#
# USO: ./generate-tokens.sh > tokens.json
# (o simplemente: bash generate-tokens.sh > tokens.json)

BASE_URL="http://localhost:8080/api/dev/generate-token"

# Pares email:qbEmpresa — PLACEHOLDERS, reemplazar localmente con datos reales
declare -a PAIRS=(
  "cliente1@ejemplo.com:Empresa Cliente 1"
  "cliente2@ejemplo.com:Empresa Cliente 2"
  "cliente3@ejemplo.com:Empresa Cliente 3"
  "cliente4@ejemplo.com:Empresa Cliente 4"
  "cliente5@ejemplo.com:Empresa Cliente 5"
  "cliente6@ejemplo.com:Empresa Cliente 6"
  "cliente7@ejemplo.com:Empresa Cliente 7"
  "cliente8@ejemplo.com:Empresa Cliente 8"
  "cliente9@ejemplo.com:Empresa Cliente 9"
  "cliente10@ejemplo.com:Empresa Cliente 10"
)

echo "["
count=${#PAIRS[@]}
i=0
for pair in "${PAIRS[@]}"; do
  email="${pair%%:*}"
  company="${pair#*:}"

  # url-encode simple del espacio (suficiente para estos nombres de empresa)
  encoded_company=$(echo "$company" | sed 's/ /%20/g' | sed 's/&/%26/g')
  encoded_email=$(echo "$email" | sed 's/ /%20/g')

  response=$(curl -s "${BASE_URL}?email=${encoded_email}&qbEmpresa=${encoded_company}")

  i=$((i+1))
  if [ "$i" -lt "$count" ]; then
    echo "  $response,"
  else
    echo "  $response"
  fi
done
echo "]"
