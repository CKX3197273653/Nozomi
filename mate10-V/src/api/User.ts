export interface User {
    id: number
    username: string
    email: string
    nickname: string
    phone: string
    status: number
    role: number
}
export interface LoginRequest {
    username: string
    password: string
}

export interface RegisterRequest {
    username: string
    password: string
    email: string
    nickname: string
    phone: string
}