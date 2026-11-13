// 不依赖 API 或 Pinia，退出登录时可在凭证失效前停止正在生成的回答。
const cancellations = new Set<() => Promise<void>>()

export function registerRagCancellation(cancel: () => Promise<void>) {
  cancellations.add(cancel)
  return () => cancellations.delete(cancel)
}

export async function cancelActiveRagTasks() {
  await Promise.allSettled([...cancellations].map(cancel => cancel()))
}
