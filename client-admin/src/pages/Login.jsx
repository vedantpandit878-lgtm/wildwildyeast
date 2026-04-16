import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { login } from '../api'

export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Username and password required')
      return
    }
    setError('')
    setLoading(true)
    try {
      const data = await login(username.trim(), password)
      localStorage.setItem('wwy_admin_token', data.token)
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-brand-black flex flex-col items-center justify-center px-6">
      <div className="w-full max-w-sm flex flex-col items-center gap-8">
        <img
          src="/logo-dark.png"
          alt="Wild Wild Yeast Ferments"
          className="w-28 h-28 object-contain"
        />

        <div className="text-center">
          <h1 className="font-display text-2xl font-bold text-brand-white">Admin</h1>
          <p className="text-brand-white/40 mt-1 text-sm">Wild Wild Yeast Ferments</p>
        </div>

        <form onSubmit={handleSubmit} className="w-full flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-2">
            <label htmlFor="username" className="text-sm font-medium text-brand-white/70">
              Username
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => { setUsername(e.target.value); setError('') }}
              autoFocus
              className="h-12 px-4 rounded-xl bg-brand-white/10 border border-brand-white/20 text-brand-white placeholder:text-brand-white/30 focus:outline-none focus:ring-2 focus:ring-brand-rust transition-all"
            />
          </div>

          <div className="flex flex-col gap-2">
            <label htmlFor="password" className="text-sm font-medium text-brand-white/70">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError('') }}
              className="h-12 px-4 rounded-xl bg-brand-white/10 border border-brand-white/20 text-brand-white placeholder:text-brand-white/30 focus:outline-none focus:ring-2 focus:ring-brand-rust transition-all"
            />
          </div>

          {error && (
            <p className="text-sm text-red-400" role="alert">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full min-h-[52px] bg-brand-rust text-brand-white font-semibold rounded-xl flex items-center justify-center gap-2 active:scale-95 disabled:opacity-60 transition-all mt-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
          >
            {loading ? (
              <><Loader2 size={18} className="animate-spin" /> Signing in…</>
            ) : (
              'Sign in'
            )}
          </button>
        </form>
      </div>
    </div>
  )
}
