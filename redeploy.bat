@REM
@REM Copyright (C) 2024 European Union
@REM  __
@REM Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
@REM European Commission – subsequent versions of the EUPL (the “Licence”);
@REM You may not use this work except in compliance with the Licence.
@REM You may obtain a copy of the Licence at:
@REM *
@REM https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
@REM *
@REM Unless required by applicable law or agreed to in writing, software distributed under
@REM the Licence is distributed on an “AS IS” basis, WITHOUT WARRANTIES OR CONDITIONS
@REM OF ANY KIND, either express or implied. See the Licence for the specific language
@REM governing permissions and limitations under the Licence.
@REM

@echo off

SET LAUNCHER="io.vertx.core.Launcher"
SET VERTICLE="io.piveau.hub.MainVerticle"
SET CMD="mvn compile"
SET VERTX_CMD="run"
SET CMD_LINE_ARGS=%*

call mvn compile dependency:copy-dependencies

java -cp  "target\dependency\*;target\classes" %LAUNCHER% %VERTX_CMD% %VERTICLE% --redeploy="src\main\**\*" --on-redeploy=%CMD% --launcher-class=%LAUNCHER% %CMD_LINE_ARGS%
