import { useEffect, useState } from 'react'
import { fetchUpcomingMatches, type Match } from './api'

const dayFormat = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })
const timeFormat = new Intl.DateTimeFormat('es-ES', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Atlantic/Canary',
})

function formatMatchDate(match: Match): string {
  if (match.dateStatus === 'PROVISIONAL') {
    const reference = new Date(`${match.matchDate}T12:00:00`)
    return `Jornada del ${dayFormat.format(reference)} · por confirmar`
  }
  const kickoff = new Date(match.kickoffAt!)
  return `${dayFormat.format(kickoff)} · ${timeFormat.format(kickoff)} (hora canaria)`
}

function App() {
  const [matches, setMatches] = useState<Match[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchUpcomingMatches().then(setMatches).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <>
      <header className="hero">
        <img src="/logo.png" alt="" className="logo" />
        <div>
          <p className="pill">¡Arriba d'ellos!</p>
          <h1>PioPio Fly</h1>
          <p className="subtitle">Vuelo y entrada para seguir a la UD fuera de casa</p>
        </div>
      </header>

      <main>
        <h2>Próximos partidos fuera</h2>
        {error && <p className="message">No se pudieron cargar los partidos. {error}</p>}
        {!error && matches === null && <p className="message">Cargando partidos…</p>}
        {matches?.length === 0 && <p className="message">No hay partidos fuera de casa próximos.</p>}
        <ul className="matches">
          {matches?.map((match) => (
            <li key={match.id} className="match">
              <p className="date">{formatMatchDate(match)}</p>
              <p className="rival">{match.homeTeam} – UD Las Palmas</p>
              <p className="venue">
                {[match.stadium, match.city].filter(Boolean).join(', ')}
                {match.airports.length > 0 && <> · ✈️ {match.airports.map((a) => a.iata).join(' / ')}</>}
              </p>
            </li>
          ))}
        </ul>
      </main>
    </>
  )
}

export default App
