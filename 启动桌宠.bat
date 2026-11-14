@echo off

cd /d "%~dp0"

if not exist ".jdk\bin\javaw.exe" (

    echo [!] 没找到JDK，请确认 DeskPet 文件夹里的 .jdk 文件夹完整

    pause

    exit /b

)

if not exist "DeskPet.class" ".jdk\bin\javac.exe" -encoding UTF-8 DeskPet.java

start "" ".jdk\bin\javaw.exe" DeskPet