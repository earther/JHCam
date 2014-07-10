set "HOMECAM_DIR=%cd%"
cd ../tomcat7/bin/
call startup.bat
cd %HOMECAM_DIR%
:start
set tmpath=D:\tmp
if exist E:\tmpe (
        set tmpath=E:\tmpe
)

java -Djava.io.tmpdir=%tmpath% -Duser.language=en -Duser.region=US -cp bin;lib/commons-codec-1.6.jar;lib/commons-lang3-3.1.jar;lib/logback-access-1.0.0.jar;lib/logback-classic-1.0.0.jar;lib/logback-core-1.0.0.jar;lib/slf4j-api-1.6.4.jar;lib/lti-civil.jar;lib/commons-logging-1.1.3.jar -Xmx1G -Djava.library.path="lib/native/win32-x86" homecam.Server
if not exist shutdown.id goto start
del shutdown.id
