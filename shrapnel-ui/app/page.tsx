"use client"
import { useEffect, useState } from "react"
import { fetchFiles, downloadFile } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Download, HardDrive } from "lucide-react"
import UploadModal from "@/components/UploadModal" // We will create this next

export default function Dashboard() {
  const [files, setFiles] = useState<any[]>([])

  const loadFiles = async () => {
    try {
      const data = await fetchFiles()
      setFiles(data)
    } catch (error) {
      console.error("Failed to load files", error)
    }
  }

  useEffect(() => {
    loadFiles()
    const interval = setInterval(loadFiles, 30000) // Auto-refresh every 30s
    return () => clearInterval(interval)
  }, [])

  return (
    <main className="p-10 max-w-6xl mx-auto space-y-8">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold flex items-center gap-2">
          <HardDrive className="w-8 h-8" />
          Project SHRAPNEL Dashboard
        </h1>
        <UploadModal onSuccess={loadFiles} />
      </div>

      <div className="border rounded-lg bg-white shadow">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Filename</TableHead>
              <TableHead>Tags</TableHead>
              <TableHead>Size (Bytes)</TableHead>
              <TableHead>Expires At</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {files.map((file) => (
              <TableRow key={file.id}>
                <TableCell className="font-medium">{file.fileName}</TableCell>
                <TableCell>
                  <div className="flex gap-1 flex-wrap">
                    {file.tags?.map((tag: string) => (
                      <Badge key={tag} variant="secondary">{tag}</Badge>
                    ))}
                  </div>
                </TableCell>
                <TableCell>{file.totalSize}</TableCell>
                <TableCell>{new Date(file.expirationTime).toLocaleString()}</TableCell>
                <TableCell className="text-right">
                  <Button variant="outline" size="sm" onClick={() => downloadFile(file.id)}>
                    <Download className="w-4 h-4 mr-2" /> Restore
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {files.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                  No active files. Upload a file to see it shattered.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </main>
  )
}