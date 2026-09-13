import request from '@/utils/request.js'

// 用户登录
export function login(username, password) {
    const params = new URLSearchParams()
    params.append('username', username)
    params.append('password', password)
    return request({
        url: '/blocker/user/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        data: params
    })
}

// 用户注册
export function register(userData) {
    return request({
        url: '/blocker/user/register',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: userData
    })
}