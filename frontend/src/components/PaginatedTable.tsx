import { Alert, Center, Group, Loader, Pagination, Stack, Table, Text, UnstyledButton } from '@mantine/core'
import type { ReactNode } from 'react'
import type { ListParams, ListParamsUpdate } from './useListParams'

export type Column<T> = {
  key: string
  header: string
  /** Champ de tri de l'API (liste blanche du spec §10.2) ; absent : colonne non triable. */
  sortKey?: string
  render: (row: T) => ReactNode
}

/** Page renvoyée par l'API ; champs facultatifs comme dans le contrat généré. */
export type PageData<T> = { items?: T[]; page?: number; totalItems?: number; totalPages?: number }

type Props<T> = {
  /** Nom accessible du tableau. */
  label: string
  columns: Column<T>[]
  data: PageData<T> | undefined
  rowKey: (row: T) => string
  params: ListParams
  setParams: (update: ListParamsUpdate) => void
  loading: boolean
  error: boolean
  emptyMessage: string
}

/** Tableau d'une liste paginée de l'API : tri par en-tête et pagination, tous deux reportés dans l'URL. */
export function PaginatedTable<T>({ label, columns, data, rowKey, params, setParams, loading, error, emptyMessage }: Props<T>) {
  const [sortField, sortDirection = 'asc'] = params.sort.split(',')

  if (error) {
    return <Alert color="red">Impossible de charger la liste.</Alert>
  }
  if (loading && data === undefined) {
    return (
      <Center py="xl">
        <Loader aria-label="Chargement" />
      </Center>
    )
  }

  const items = data?.items ?? []
  const totalItems = data?.totalItems ?? 0
  const totalPages = data?.totalPages ?? 1

  const toggleSort = (sortKey: string) =>
    setParams({ sort: sortField === sortKey && sortDirection === 'asc' ? `${sortKey},desc` : `${sortKey},asc` })

  return (
    <Stack gap="sm">
      <Table.ScrollContainer minWidth={720}>
        <Table aria-label={label} striped highlightOnHover verticalSpacing="xs">
          <Table.Thead>
            <Table.Tr>
              {columns.map((column) => {
                const sorted = column.sortKey !== undefined && column.sortKey === sortField
                return (
                  <Table.Th
                    key={column.key}
                    aria-sort={sorted ? (sortDirection === 'desc' ? 'descending' : 'ascending') : undefined}
                  >
                    {column.sortKey === undefined ? (
                      column.header
                    ) : (
                      <UnstyledButton fw={700} fz="sm" onClick={() => toggleSort(column.sortKey as string)}>
                        {column.header}
                        <Text component="span" aria-hidden="true" c="dimmed" ml={4}>
                          {sorted ? (sortDirection === 'desc' ? '▼' : '▲') : '↕'}
                        </Text>
                      </UnstyledButton>
                    )}
                  </Table.Th>
                )
              })}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {items.map((row) => (
              <Table.Tr key={rowKey(row)}>
                {columns.map((column) => (
                  <Table.Td key={column.key}>{column.render(row)}</Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {items.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {emptyMessage}
        </Text>
      )}
      <Group justify="space-between">
        <Text size="sm" c="dimmed">
          {totalItems} élément{totalItems > 1 ? 's' : ''}
        </Text>
        {totalPages > 1 && (
          <Pagination
            total={totalPages}
            value={params.page + 1}
            onChange={(value) => setParams({ page: value - 1 })}
            getControlProps={(control) => ({
              'aria-label': control === 'previous' ? 'Page précédente' : control === 'next' ? 'Page suivante' : undefined,
            })}
          />
        )}
      </Group>
    </Stack>
  )
}
