import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { lookupOrCreateCustomer, createCustomer } from '../api/customers'

export default function Login() {
  const navigate = useNavigate()
  const [step, setStep] = useState('flat') // 'flat' | 'name'
  const [flat, setFlat] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleFlatSubmit(e) {
    e.preventDefault()
    if (!flat.trim()) { setError("That flat doesn't look right"); return }
    setError('')
    setLoading(true)
    try {
      const data = await lookupOrCreateCustomer(flat.trim())
      if (data.new_customer) {
        setStep('name')
      } else {
        localStorage.setItem('wwy_flat', data.flat_number)
        localStorage.setItem('wwy_name', data.name)
        navigate('/shop')
      }
    } catch {
      setError("That flat doesn't look right")
    } finally {
      setLoading(false)
    }
  }

  async function handleNameSubmit(e) {
    e.preventDefault()
    if (!name.trim()) { setError('We need to know who to deliver to!'); return }
    setError('')
    setLoading(true)
    try {
      const data = await createCustomer(flat.trim(), name.trim())
      localStorage.setItem('wwy_flat', data.flat_number)
      localStorage.setItem('wwy_name', data.name)
      navigate('/shop')
    } catch {
      setError('Something went wrong. Try again?')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-brand-cream flex flex-col items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm flex flex-col items-center gap-8">
        {/* Logo */}
        <div className="w-36 h-36 flex items-center justify-center">
          <img
            src="/logo-light.png"
            alt="Wild Wild Yeast Ferments"
            className="w-full h-full object-contain"
          />
        </div>

        <div className="text-center">
          <h1 className="font-display text-3xl font-bold text-brand-charcoal">Wild Wild Yeast</h1>
          <p className="text-brand-charcoal/60 mt-2 text-base">Good bread, honestly made.</p>
        </div>

        {step === 'flat' ? (
          <form onSubmit={handleFlatSubmit} className="w-full flex flex-col gap-4" noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="flat" className="text-sm font-medium text-brand-charcoal">
                Your flat number
              </label>
              <input
                id="flat"
                type="text"
                inputMode="numeric"
                placeholder="e.g. 4B or 12"
                value={flat}
                onChange={(e) => { setFlat(e.target.value); setError('') }}
                autoFocus
                autoComplete="off"
                className="w-full h-14 px-4 rounded-2xl border border-gray-200 bg-brand-white text-brand-charcoal text-lg placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-rust transition-all"
                aria-describedby={error ? 'flat-error' : undefined}
                aria-invalid={!!error}
              />
              {error && (
                <p id="flat-error" className="text-sm text-red-600" role="alert">
                  {error}
                </p>
              )}
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full min-h-[56px] bg-brand-rust text-brand-white font-semibold text-base rounded-2xl flex items-center justify-center gap-2 active:scale-95 disabled:opacity-60 transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            >
              {loading ? <Loader2 size={20} className="animate-spin" /> : "Let's go →"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleNameSubmit} className="w-full flex flex-col gap-4" noValidate>
            <p className="text-brand-charcoal/70 text-center">
              First time here? What should we call you?
            </p>
            <div className="flex flex-col gap-2">
              <label htmlFor="name" className="text-sm font-medium text-brand-charcoal">
                Your name
              </label>
              <input
                id="name"
                type="text"
                placeholder="e.g. Priya Shah"
                value={name}
                onChange={(e) => { setName(e.target.value); setError('') }}
                autoFocus
                autoComplete="name"
                className="w-full h-14 px-4 rounded-2xl border border-gray-200 bg-brand-white text-brand-charcoal text-lg placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-rust transition-all"
                aria-describedby={error ? 'name-error' : undefined}
                aria-invalid={!!error}
              />
              {error && (
                <p id="name-error" className="text-sm text-red-600" role="alert">
                  {error}
                </p>
              )}
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full min-h-[56px] bg-brand-rust text-brand-white font-semibold text-base rounded-2xl flex items-center justify-center gap-2 active:scale-95 disabled:opacity-60 transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            >
              {loading ? <Loader2 size={20} className="animate-spin" /> : 'Save and continue →'}
            </button>
            <button
              type="button"
              onClick={() => { setStep('flat'); setError('') }}
              className="text-brand-charcoal/50 text-sm underline underline-offset-2 py-2"
            >
              Back
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
