package junktag

type Generator interface {
	Generate() []byte
}

type newGenerator func(string) (Generator, error)

type BytesGenerator struct {
}

func (b *BytesGenerator) Generate() []byte {
	return nil
}
