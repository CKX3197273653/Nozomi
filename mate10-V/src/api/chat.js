import request from '@/utils/request'

export function createChat(userId, title) {
    return request.post('/ai/chat', { userId, title })
}

export function chatRecord(data) {
    return request.post('/ai/chatRecord', data)
}

export async function chatStream(data, onChunk) {
    const token = localStorage.getItem('token')

    const resp = await fetch('/ai/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify(data)
    })

    if (!resp.ok) {
        throw new Error('HTTP ' + resp.status)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 按 \n\n 切分出完整消息
        let idx
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
            const raw = buffer.slice(0, idx)
            buffer = buffer.slice(idx + 2)

            let eventName = 'message'
            let data = ''
            for (const line of raw.split('\n')) {
                if (line.startsWith('event:')) {
                    eventName = line.slice(6).trim()
                } else if (line.startsWith('data:')) {
                    data += line.slice(5)
                }
            }
            if (eventName === 'done') {
                reader.cancel()
                return
            }
            if (data) onChunk(data)
        }
    }
}