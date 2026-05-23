import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchPhotoAssetBlob } from "../api";
import type { PhotoAssetType } from "../types";

type UsePhotoAssetPreviewOptions = {
  assetType: Exclude<PhotoAssetType, "ORIGINAL"> | null;
  officeId: number | null;
  photoId: number;
  token: string;
};

export function usePhotoAssetPreview({ assetType, officeId, photoId, token }: UsePhotoAssetPreviewOptions) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const previewQuery = useQuery({
    enabled: Boolean(assetType && officeId && token),
    queryKey: ["photo-asset-preview", officeId, photoId, assetType],
    queryFn: async () => {
      if (!assetType || !officeId) {
        throw new Error("사진 미리보기 컨텍스트가 없습니다.");
      }
      return fetchPhotoAssetBlob(token, officeId, photoId, assetType);
    },
    staleTime: 5 * 60 * 1000
  });

  useEffect(() => {
    if (!previewQuery.data) {
      setObjectUrl(null);
      return;
    }
    const nextUrl = window.URL.createObjectURL(previewQuery.data);
    setObjectUrl(nextUrl);
    return () => {
      window.URL.revokeObjectURL(nextUrl);
    };
  }, [previewQuery.data]);

  return {
    error: previewQuery.error,
    loading: previewQuery.isLoading,
    url: objectUrl
  };
}
