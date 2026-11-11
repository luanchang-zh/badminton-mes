import { get, post, del } from '@/utils/request'
import type { LoginResult } from './auth'

export interface FaceProfile {
  enrolled: boolean
  faceLoginAllowed: boolean
  faceCount: number
  maxFaces: number
  samples: { faceId: string; createdAt: string }[]
}

export interface FaceEnrollmentResult {
  acceptedCount: number
  faceCount: number
  rejectedImages: { index: number; reason: string }[]
}

export const getFaceProfile = () => get<FaceProfile>('/system/face/profile')
export const disableFaceLogin = () => del<void>('/system/face/enrollment')

export function enrollFace(images: Blob[]): Promise<FaceEnrollmentResult> {
  const data = new FormData()
  images.forEach((image, index) => data.append('images', image, `face-${index + 1}.jpg`))
  data.append('consent', 'true')
  return post('/system/face/enroll', data, { timeout: 60000 })
}

export interface FaceLoginRequestOptions {
  signal?: AbortSignal
  timeout?: number
}

export function loginWithFace(image: Blob, options: FaceLoginRequestOptions = {}): Promise<LoginResult> {
  const data = new FormData()
  data.append('image', image, 'face-login.jpg')
  return post('/system/auth/face_login', data, { timeout: options.timeout ?? 5000, signal: options.signal, silent: true })
}
