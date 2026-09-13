import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user',()=>{
    const user = ref(null)
    const token = ref(null)

    function setUser(userData) {
        user.value = userData
    }

    function setToken(newToken) {
        token.value = newToken
        localStorage.setItem('token', newToken)
    }

    function logout() {
        user.value = null
        token.value = null
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    }
    return { user, token, setUser, setToken, logout }
})