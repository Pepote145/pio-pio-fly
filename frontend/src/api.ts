export type DateStatus = 'PROVISIONAL' | 'CONFIRMED'

export interface Airport {
  iata: string
  note: string | null
}

/** Partido fuera de casa, tal y como lo devuelve GET /api/matches. */
export interface Match {
  id: number
  competition: string
  season: string | null
  gameweek: number | null
  homeTeam: string
  homeTeamSlug: string
  /** Día del partido (yyyy-MM-dd); si es provisional, el día de referencia de la jornada. */
  matchDate: string
  /** Hora de inicio en UTC, o null mientras la fecha sea provisional. */
  kickoffAt: string | null
  dateStatus: DateStatus
  stadium: string | null
  city: string | null
  airports: Airport[]
}

export async function fetchUpcomingMatches(): Promise<Match[]> {
  const response = await fetch('/api/matches')
  if (!response.ok) {
    throw new Error(`Error ${response.status} al cargar los partidos`)
  }
  return response.json()
}
