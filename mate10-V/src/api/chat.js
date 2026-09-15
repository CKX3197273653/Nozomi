import request from '@/utils/request'

export function createChat(userId,title) {
    return request.post('/ai/chat',{userId,title})
}
export function chatRecord(data) {
    return request.post('/ai/chatRecord',data)
}