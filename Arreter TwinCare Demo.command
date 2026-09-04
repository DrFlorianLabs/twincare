#!/bin/zsh

set -u

project_dir="${0:A:h}"
runtime_dir="$project_dir/.twincare-demo"
pid_file="$runtime_dir/server.pid"
health_url="http://127.0.0.1:8088/demo/api/health"

if [[ ! -f "$pid_file" ]]; then
  echo "TwinCare Demo est déjà arrêté."
  exit 0
fi

server_pid="$(<"$pid_file")"
if [[ "$server_pid" != <-> ]]; then
  echo "Le fichier d’état est invalide ; aucun processus n’a été arrêté."
  rm -f "$pid_file"
  exit 1
fi

if ! kill -0 "$server_pid" 2>/dev/null; then
  echo "TwinCare Demo est déjà arrêté."
  rm -f "$pid_file"
  exit 0
fi

server_command="$(ps -p "$server_pid" -o command= 2>/dev/null || true)"
expected_jar="$project_dir/BackEnd/target/backend-0.0.1-SNAPSHOT.jar"
if [[ "$server_command" != *"$expected_jar"* ]]; then
  echo "Le processus enregistré n’est pas TwinCare Demo ; il n’a pas été arrêté."
  rm -f "$pid_file"
  exit 1
fi

if ! curl --fail --silent --connect-timeout 1 --max-time 2 "$health_url" >/dev/null 2>&1; then
  echo "TwinCare Demo ne répond plus ; arrêt du processus enregistré…"
fi

kill "$server_pid" 2>/dev/null || true
for attempt in {1..20}; do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    rm -f "$pid_file"
    echo "TwinCare Demo est arrêté."
    exit 0
  fi
  sleep 0.5
done

echo "TwinCare Demo est encore en cours d’arrêt. Réessayez dans quelques secondes."
exit 1
