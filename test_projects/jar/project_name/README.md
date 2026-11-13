# Static API JAR Workload

This dependency-free Java application provides static HTTP responses for testing a JAR workload through the cloud service gateway.

## Routes

| Method | Path | Status | Response |
| --- | --- | ---: | --- |
| `GET` | `/get` | 200 | Static service response |
| `GET` | `/get/health` | 200 | `{"status":"UP"}` |
| `GET` | `/get/items` | 200 | Static list of two items |
| `POST` | `/post` | 200 | Static POST response |
| `POST` | `/post/created` | 201 | Static created-item response |

Unknown paths return 404. Using the wrong method for a known path returns 405.

## Build

From this directory:

```powershell
mvn package
```

The executable JAR is `target\static-api.jar`. The server reads `--server.port=...` (injected by the JAR runtime), then `PORT` or `SERVER_PORT`, and defaults to port 8080 when run standalone.

## Test through the cloud service

From the repository root, build this project and start the cloud service (`org.cloudServices.Main`) in another terminal. Then create and run a compute unit:

```powershell
cd test_projects\jar\project_name
mvn package
cd ..\..\..
.\cld cu create --name static-api --capacity MICRO jar test_projects\jar\project_name\target\static-api.jar
.\cld cu run static-api
```

The gateway is available on port 8000. Use the compute unit name as the route prefix:

```powershell
curl.exe -i http://127.0.0.1:8000/static-api/get
curl.exe -i http://127.0.0.1:8000/static-api/get/health
curl.exe -i http://127.0.0.1:8000/static-api/get/items
curl.exe -i -X POST -H "Content-Type: application/json" -d "{\"sample\":true}" http://127.0.0.1:8000/static-api/post
curl.exe -i -X POST http://127.0.0.1:8000/static-api/post/created
```

Allow a moment for the JAR process to start before sending requests. When finished:

```powershell
.\cld cu kill static-api
```
