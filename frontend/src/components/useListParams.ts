import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'

/** Paramètres d'une liste paginée (spec §10.2) ; `page` commence à 0, comme dans l'API. */
export type ListParams = {
  page: number
  size: number
  sort: string
  /** Recherche `q` et filtres, tels qu'ils figurent dans l'URL. */
  filters: Readonly<Record<string, string>>
}

/** Modification partielle : un filtre vide ou `null` est retiré. */
export type ListParamsUpdate = {
  page?: number
  size?: number
  sort?: string
  filters?: Record<string, string | null | undefined>
}

const DEFAULT_SIZE = 20
const RESERVED = new Set(['page', 'size', 'sort'])

function toInteger(value: string | null, fallback: number, min: number): number {
  const parsed = value === null ? Number.NaN : Number.parseInt(value, 10)
  return Number.isInteger(parsed) && parsed >= min ? parsed : fallback
}

/**
 * Pagination, tri et filtres d'une liste, synchronisés avec l'URL (spec §12.3) : la page se partage et se recharge
 * telle quelle. Les valeurs par défaut sont omises de l'URL. Changer de filtre, de tri ou de taille ramène à la
 * première page.
 */
export function useListParams(defaults: { sort: string; size?: number }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const defaultSize = defaults.size ?? DEFAULT_SIZE
  const defaultSort = defaults.sort

  const params = useMemo<ListParams>(() => {
    const filters: Record<string, string> = {}
    searchParams.forEach((value, key) => {
      if (!RESERVED.has(key) && value !== '') {
        filters[key] = value
      }
    })
    return {
      page: toInteger(searchParams.get('page'), 0, 0),
      size: toInteger(searchParams.get('size'), defaultSize, 1),
      sort: searchParams.get('sort') || defaultSort,
      filters,
    }
  }, [searchParams, defaultSize, defaultSort])

  const setParams = useCallback(
    (update: ListParamsUpdate) => {
      const resetsPage = update.filters !== undefined || update.sort !== undefined || update.size !== undefined
      const page = update.page ?? (resetsPage ? 0 : params.page)
      const size = update.size ?? params.size
      const sort = update.sort ?? params.sort
      const filters: Record<string, string | null | undefined> = { ...params.filters, ...update.filters }

      const next = new URLSearchParams()
      Object.entries(filters).forEach(([key, value]) => {
        if (value) {
          next.set(key, value)
        }
      })
      if (page !== 0) next.set('page', String(page))
      if (size !== defaultSize) next.set('size', String(size))
      if (sort !== defaultSort) next.set('sort', sort)
      setSearchParams(next, { replace: true })
    },
    [params, defaultSize, defaultSort, setSearchParams],
  )

  return { params, setParams }
}
