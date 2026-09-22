export async function agentStream(question, handlers = {}) {
    const { onStart, onTool, onMessage, onError, onDone } = handlers
    const token = localStorage.getItem('token')

    const resp = await fetch('/ai/qc-agent/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify({ question })
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

        let idx
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
            const raw = buffer.slice(0, idx)
            buffer = buffer.slice(idx + 2)

            let eventName = 'message'
            const dataLines = []

            for (const line of raw.split('\n')) {
                if (line.startsWith('event:')) {
                    eventName = line.slice(6).trim()
                } else if (line.startsWith('data:')) {
                    dataLines.push(line.slice(5).replace(/^ /, ''))
                }
            }
            const data = dataLines.join('\n')

            switch (eventName) {
                case 'start':
                    if (onStart) onStart(data)
                    break

                case 'tool':
                    try {
                        if (onTool) onTool(JSON.parse(data))
                    } catch (e) {
                        console.warn('[agent] tool 事件解析失败：', data)
                    }
                    break

                case 'message':
                    if (onMessage) onMessage(data)
                    break

                case 'error':
                    if (onError) onError(data)
                    break

                case 'done':
                    reader.cancel()
                    if (onDone) onDone()
                    return
            }
        }
    }
}
