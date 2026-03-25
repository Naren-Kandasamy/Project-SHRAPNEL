"use client"
import { useState } from "react"
import { apiClient } from "@/lib/api"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { Badge } from "@/components/ui/badge"
import { Upload, X } from "lucide-react"

export default function UploadModal({ onSuccess }: { onSuccess: () => void }) {
  const [open, setOpen] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [expiration, setExpiration] = useState(60)
  const [tags, setTags] = useState<string[]>([])
  const [currentTag, setCurrentTag] = useState("")
  const [progress, setProgress] = useState(0)
  const [isUploading, setIsUploading] = useState(false)

  const handleAddTag = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && currentTag.trim() !== '') {
      e.preventDefault()
      if (!tags.includes(currentTag.trim())) {
        setTags([...tags, currentTag.trim()])
      }
      setCurrentTag("")
    }
  }

  const removeTag = (tagToRemove: string) => {
    setTags(tags.filter(tag => tag !== tagToRemove))
  }

  const handleUpload = async () => {
    if (!file) return

    setIsUploading(true)
    const formData = new FormData()
    formData.append("file", file)
    formData.append("expirationMinutes", expiration.toString())
    tags.forEach(tag => formData.append("tags", tag)) // Spring can read duplicate keys as a List

    try {
      await apiClient.post("/shatter", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round((progressEvent.loaded * 100) / (progressEvent.total || 1))
          setProgress(percentCompleted)
        }
      })
      setOpen(false)
      onSuccess() // Refresh dashboard
      // Reset form
      setFile(null); setTags([]); setProgress(0); setExpiration(60)
    } catch (error) {
      console.error("Upload failed", error)
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button><Upload className="w-4 h-4 mr-2" /> Shatter New File</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Upload & Shatter</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-4">
          
          <div className="space-y-2">
            <label className="text-sm font-medium">Select File</label>
            <Input type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} disabled={isUploading} />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">Expiration (Minutes)</label>
            <Input type="number" value={expiration} onChange={(e) => setExpiration(Number(e.target.value))} disabled={isUploading} />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">Tags (Press Enter to add)</label>
            <Input 
              value={currentTag} 
              onChange={(e) => setCurrentTag(e.target.value)} 
              onKeyDown={handleAddTag} 
              placeholder="e.g. secret, project-x"
              disabled={isUploading}
            />
            <div className="flex flex-wrap gap-2 mt-2">
              {tags.map(tag => (
                <Badge key={tag} className="flex items-center gap-1">
                  {tag} 
                  <X className="w-3 h-3 cursor-pointer" onClick={() => !isUploading && removeTag(tag)} />
                </Badge>
              ))}
            </div>
          </div>

          {isUploading && (
            <div className="space-y-2">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Uploading & Shattering...</span>
                <span>{progress}%</span>
              </div>
              <Progress value={progress} />
            </div>
          )}

          <Button className="w-full" onClick={handleUpload} disabled={!file || isUploading}>
            {isUploading ? "Processing..." : "Shatter Data"}
          </Button>

        </div>
      </DialogContent>
    </Dialog>
  )
}