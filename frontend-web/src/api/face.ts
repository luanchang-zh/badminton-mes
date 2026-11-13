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
  return uploadEnrollment('/system/face/enroll', images)
}

export function reenrollFace(images: Blob[]): Promise<FaceEnrollmentResult> {
  return uploadEnrollment('/system/face/reenroll', images)
}

function uploadEnrollment(path: string, images: Blob[]): Promise<FaceEnrollmentResult> {
  const data = new FormData()
  images.forEach((image, index) => data.append('images', image, `face-${index + 1}.jpg`))
  data.append('consent', 'true')
  return post(path, data, { timeout: 60000, silent: true })
}

export interface FaceLoginRequestOptions {
  signal?: AbortSignal
  timeout?: number
}

export const FACE_LOGIN_TIMEOUT_MS = 30_000

export function loginWithFace(image: Blob, options: FaceLoginRequestOptions = {}): Promise<LoginResult> {
  const data = new FormData()
  data.append('image', image, 'face-login.jpg')
  return post('/system/auth/face_login', data, { timeout: options.timeout ?? FACE_LOGIN_TIMEOUT_MS, signal: options.signal, silent: true })
}
