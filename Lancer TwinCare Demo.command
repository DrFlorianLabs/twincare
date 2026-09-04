#!/bin/zsh

set -u

project_dir="${0:A:h}"
runtime_dir="$project_dir/.twincare-demo"
pid_file="$runtime_dir/server.pid"
log_file="$runtime_dir/server.log"
demo_url="http://127.0.0.1:8088/demo/"
health_url="http://127.0.0.1:8088/demo/api/health"
expected_jar="$project_dir/BackEnd/target/backend-0.0.1-SNAPSHOT.jar"

mkdir -p "$runtime_dir"

if [[ -f "$pid_file" ]]; then
  running_pid="$(<"$pid_file")"
  if [[ "$running_pid" == <-> ]] && kill -0 "$running_pid" 2>/dev/null; then
    running_command="$(ps -p "$running_pid" -o command= 2>/dev/null || true)"
    if [[ "$running_command" == *"$expected_jar"* ]]; then
      if curl --fail --silent --connect-timeout 1 --max-time 2 "$health_url" >/dev/null 2>&1; then
        echo "TwinCare Demo est déjà prêt."
        open "$demo_url"
        exit 0
      fi
      echo "TwinCare Demo est lancé mais ne répond pas encore."
      echo "Utilisez « Arreter TwinCare Demo », puis relancez la démonstration."
      exit 1
    fi
  fi
  rm -f "$pid_file"
fi

java_home_path=""
if [[ -x /usr/libexec/java_home ]]; then
  java_home_path="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi
if [[ -z "$java_home_path" && -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  java_home_path="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi
if [[ -z "$java_home_path" || ! -x "$java_home_path/bin/java" ]]; then
  echo "Java 21 est nécessaire pour lancer la démonstration."
  echo "Installez OpenJDK 21 puis double-cliquez de nouveau sur ce fichier."
  read "?Appuyez sur Entrée pour fermer."
  exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "OpenSSL est nécessaire pour générer les secrets éphémères de la démonstration."
  read "?Appuyez sur Entrée pour fermer."
  exit 1
fi

if ! "$java_home_path/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "21([.]|\")'; then
  echo "La version Java détectée n’est pas Java 21."
  read "?Appuyez sur Entrée pour fermer."
  exit 1
fi

echo "Préparation de TwinCare Demo…"
if ! JAVA_HOME="$java_home_path" PATH="$java_home_path/bin:$PATH" \
    sh "$project_dir/BackEnd/mvnw" -q -f "$project_dir/BackEnd/pom.xml" \
    -Pdemo -DskipTests clean package; then
  echo "La préparation a échoué. Consultez les messages ci-dessus."
  read "?Appuyez sur Entrée pour fermer."
  exit 1
fi

demo_jwt_secret="$(openssl rand -base64 48 | tr -d '\n')"
demo_patient_pepper="$(openssl rand -base64 48 | tr -d '\n')"

cd "$project_dir" || exit 1
nohup env \
  SPRING_PROFILES_ACTIVE=demo \
  DEMO_PORT=8088 \
  JWT_SECRET="$demo_jwt_secret" \
  PATIENT_INVITATION_TOKEN_PEPPER="$demo_patient_pepper" \
  "$java_home_path/bin/java" -jar "$project_dir/BackEnd/target/backend-0.0.1-SNAPSHOT.jar" \
  >"$log_file" 2>&1 </dev/null &
server_pid=$!
echo "$server_pid" > "$pid_file"

for attempt in {1..90}; do
  if curl --fail --silent --connect-timeout 1 --max-time 2 "$health_url" >/dev/null 2>&1; then
    echo "TwinCare Demo est prêt : $demo_url"
    echo "Vous pouvez fermer cette fenêtre."
    open "$demo_url"
    exit 0
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "TwinCare Demo n’a pas pu démarrer."
    tail -30 "$log_file"
    rm -f "$pid_file"
    read "?Appuyez sur Entrée pour fermer."
    exit 1
  fi
  sleep 2
done

kill "$server_pid" 2>/dev/null || true
rm -f "$pid_file"
echo "Le démarrage prend trop de temps. Le processus a été arrêté ; réessayez."
exit 1
