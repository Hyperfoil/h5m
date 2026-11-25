import { useQuery, type QueryKey, type UseQueryOptions } from '@tanstack/react-query';
import { useState } from 'react';

export function usePaginatedQuery<TData, TRow, TError, TKey extends QueryKey>(
  options: UseQueryOptions<TData, TError, TData, TKey>,
  getRows: (data: TData) => TRow[],
  initialPageSize = 10,
) {
  const [requestedPage, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);

  const { data: totalItems = 0 } = useQuery({
    ...options,
    select: (data) => getRows(data).length,
  });

  const page = requestedPage

  const { data: pageRows = [] } = useQuery({
    ...options,
    select: (data) => getRows(data).slice((page - 1) * pageSize, page * pageSize),
  });

  const onChange = ({ page, pageSize }: { page: number; pageSize: number }) => {
    setPage(page);
    setPageSize(pageSize);
  };

  return { pageRows, totalItems, page, pageSize, onChange };
}