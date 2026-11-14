# MES 人脸识别服务

MES 后端通过 `mes-face` 网络连接 `http://insightface:8080`。宿主机可通过 `127.0.0.1:18097` 检查服务。

## 切换至 NVIDIA GPU

Windows 使用 Docker Desktop 的 WSL 2 后端和支持 WSL 的 NVIDIA 驱动。Compose 使用含 CUDA 12.9.1 的 InsightFace Server 0.3.1 CUDA 镜像，Windows 对应的 NVIDIA 驱动版本为 576.57 或更新版本。Linux 使用 NVIDIA 驱动和 NVIDIA Container Toolkit。先用 `nvidia-smi` 检查驱动，再确认 Linux 容器能看到 GPU。 [Docker GPU 文档](https://docs.docker.com/desktop/features/gpu/) · [NVIDIA CUDA 12.9 Update 1 发布说明](https://docs.nvidia.com/cuda/archive/12.9.1/cuda-toolkit-release-notes/index.html)

从 InsightFace Server 0.3.1 源码目录运行下面的 PowerShell 命令。将 `$mesRepo` 设为 MES 仓库目录，并使用现有部署的项目名和本地环境文件。启用鉴权时，InsightFace 的 `INSIGHTFACE_API_KEY` 和 MES 的 `MES_INSIGHTFACE_API_KEY` 需要一致。

```powershell
$mesRepo = 'D:\专业工程实训\badminton-mes'
$faceCompose = @(
    '--env-file', '.env.local',
    '-p', 'badminton-insightface',
    '-f', 'server/deploy/compose.cpu.yml',
    '-f', (Join-Path $mesRepo 'deploy/insightface/compose.mes.yml'),
    '-f', (Join-Path $mesRepo 'deploy/insightface/compose.gpu.yml')
)
docker compose @faceCompose config --quiet
docker compose @faceCompose pull server
docker compose @faceCompose up -d --wait --wait-timeout 180 server
Invoke-RestMethod http://127.0.0.1:18097/v1/health
```

GPU 配置沿用 CPU 部署的模型目录、SQLite 数据卷和本机端口。它向容器请求 GPU，并以严格 CUDA 模式启动检测和识别，GPU 初始化失败时服务会报错。确认 `/v1/health` 返回 `ready`，再用服务密钥查询 `/v1/models`，确认运行提供程序为 `CUDAExecutionProvider`。

首次部署时可通过同一组 Compose 参数运行 `docker compose @faceCompose run --rm models install buffalo_l`，按安装器提示查看模型许可。切回 CPU 时，从命令中去掉 `compose.gpu.yml` 并重新启动 `server`。
