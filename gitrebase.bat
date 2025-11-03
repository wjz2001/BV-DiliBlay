@echo off
setlocal

:: ==============================
:: 用户配置区域 (请根据实际情况修改)
:: ==============================
:: 你的工作分支名称
set MY_BRANCH=feature
:: 原作者(upstream)的主分支名称 (通常是 main 或 master)
set UPSTREAM_BRANCH=feature
:: ==============================

title Git Rebase 助手

echo ========================================================
echo       Git Fork 变基助手 (当前分支: %MY_BRANCH%)
echo ========================================================
echo.
echo  [1] 开始正常变基 (Fetch + Rebase + Force Push)
echo      - 适用场景: 刚开始准备同步原作者代码
echo.
echo  [2] 冲突已解决，继续变基 (Add . + Continue + Force Push)
echo      - 适用场景: 解决完冲突后，直接提交并完成后续操作
echo.
echo ========================================================
set /p choice=请输入选项 (1 或 2): 

if "%choice%"=="1" goto OP_START
if "%choice%"=="2" goto OP_CONTINUE
echo 无效的选项，按任意键退出...
pause >nul
exit

:OP_START
echo.
echo [1/4] 正在切换到 %MY_BRANCH% 分支...
git checkout %MY_BRANCH%
if errorlevel 1 goto ERROR

echo.
echo [2/4] 正在抓取 upstream 更新...
git fetch upstream
if errorlevel 1 goto ERROR

echo.
echo [3/4] 正在执行变基 (Rebase upstream/%UPSTREAM_BRANCH%)...
git rebase upstream/%UPSTREAM_BRANCH%
if errorlevel 1 (
    color 0C
    echo.
    echo [!] 发生冲突！变基已暂停。
    echo ------------------------------------------------
    echo 请手动打开文件解决冲突。
    echo 解决完毕后，请重新运行此脚本并选择 [2] 选项。
    echo ------------------------------------------------
    pause
    exit
)
goto PUSH_STEP

:OP_CONTINUE
echo.
echo [1/3] 正在添加所有变动 (git add .)...
git add .
if errorlevel 1 goto ERROR

echo.
echo [2/3] 正在继续变基 (git rebase --continue)...
git rebase --continue
if errorlevel 1 (
    color 0C
    echo.
    echo [!] 依然存在冲突或错误，请再次解决后重试。
    pause
    exit
)
goto PUSH_STEP

:PUSH_STEP
echo.
echo [最终步] 正在强制推送 (Force Push) 到 origin...
git push -f origin %MY_BRANCH%
if errorlevel 1 goto ERROR

echo.
echo ==========================================
echo       恭喜！变基同步成功完成！
echo ==========================================
pause
exit

:ERROR
color 0C
echo.
echo [!] 发生错误，请检查上方的 Git 报错信息。
pause
exit