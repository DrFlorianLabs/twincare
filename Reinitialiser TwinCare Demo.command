#!/bin/zsh

set -u

demo_url="http://127.0.0.1:8088/demo/"
reset_url="http://127.0.0.1:8088/demo/api/reset"

if curl --fail --silent --connect-timeout 1 --max-time 3 \
    --request POST "$reset_url" >/dev/null 2>&1; then
  echo "Les données fictives ont été réinitialisées."
  open "$demo_url"
  exit 0
fi

echo "TwinCare Demo n’est pas démarré. Lancez d’abord « Lancer TwinCare Demo.command »."
exit 1
